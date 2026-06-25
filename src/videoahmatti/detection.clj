(ns videoahmatti.detection
  (:require
   [clojure.java.shell :as shell]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [jsonista.core :as json]
   [videoahmatti.db :as db])
  (:import
   [java.nio.file Files]))

(defn- extract-images-from-video [video-path]
  (let [image-every-n-secs 5
        temp-dir (Files/createTempDirectory "videoahmatti-frames" (make-array java.nio.file.attribute.FileAttribute 0))]
    (try
      (let [result (shell/sh "ffmpeg"
                             "-i" video-path
                             "-vf" (str "fps=1/" image-every-n-secs)
                             (str (.toString temp-dir) "/frame-%04d.jpg"))]
        (if (zero? (:exit result))
          temp-dir
          (throw (ex-info (or (:err result) "ffmpeg failed") {}))))
      (catch Exception e
        (Files/deleteIfExists temp-dir)
        (throw e)))))

(def ^:private json-mapper
  (json/object-mapper {:decode-key-fn keyword}))

#_[["vehicle" 0.9838]
   ["white-tailed deer" 0.982]
   ["bobcat" 0.8206]
   ["mule deer" 0.0896]
   ["sika deer" 0.0752]
   ["red deer" 0.045]
   ["cervidae family" 0.0449]
   ["black-tailed jackrabbit" 0.0251]
   ["domestic cat" 0.021]
   ["domestic cattle" 0.0157]
   ["lepus species" 0.013]]
#_(defn- read-predictions-json [predictions-json-path]
    (->> (:predictions (json/read-value (slurp predictions-json-path) json-mapper))
         (map :classifications)
         (map (fn [i]
                (zipmap (map #(last (str/split % #";")) (:classes i))
                        (:scores i))))
         (apply merge-with max)
         (util/filter-map-vals #(>= % 0.01))
         (#(dissoc % "blank"))

         (sort-by val >)
         vec))

(defn- read-predictions-json [predictions-json-path]
  #_(prn "read-predictions-json" predictions-json-path
         (->> (:predictions (json/read-value (slurp "data/predictions.json") json-mapper))
              (map #(select-keys % [:prediction :prediction_score :classifications]))))
  (->> (:predictions (json/read-value (slurp predictions-json-path) json-mapper))
       (map (fn [x] {(-> x :prediction (str/split #";") last)
                     (:prediction_score x)}))
       (apply merge-with max)
       (#(dissoc % "blank" "no cv result"))
       (sort-by val >)
       vec))

(defn- detect-video-animals [video-path]
  (log/infof "Starting animal detection for %s" video-path)
  (let [images-path (extract-images-from-video video-path)
        predictions-json-path (str images-path "/predictions.json")
        result (shell/sh "venv/bin/python"
                         "-m"
                         "speciesnet.scripts.run_model"
                         "--noprogress_bars"
                         "--bypass_prompts"
                         "--country"
                         "FIN"
                         "--folders"
                         (str images-path)
                         "--predictions_json"
                         predictions-json-path)]
    (if (zero? (:exit result))
      (try
        (let [detections (read-predictions-json predictions-json-path)]
          (log/infof "Animal detection completed for video=%s results=%s"
                     video-path
                     detections)
          {:ok? true
           :detections detections})
        (catch Exception e
          {:ok? false
           :error (or (.getMessage e) "invalid-detection-json")
           :raw-output (:out result)}))
      {:ok? false
       :error (or (:err result) "speciesnet command failed")})))

(defn- run-undetected-video-detection-loop! [datasource]
  (loop [processed 0]
    (if-let [video (db/find-next-undetected-video datasource)]
      (let [detection (detect-video-animals (:storage_path video))]
        (if (:ok? detection)
          (do
            (db/set-video-detections! datasource (:id video) (:detections detection))
            (recur (inc processed)))
          (do
            (log/errorf "Animal detection failed for video id=%s: %s"
                        (:id video)
                        (:error detection))
            {:processed processed
             :failed-video-id (:id video)
             :status :failed})))
      {:processed processed
       :status :done})))

(defn run-detection-pass! [datasource]
  (let [result (run-undetected-video-detection-loop! datasource)]
    (when (not= result {:processed 0 :status :done})
      (log/infof "Animal detection pass finished: %s" result))
    result))
