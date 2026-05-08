(ns videoahmatti.workers
  (:require
   [clojure.java.shell :as shell]))

(defonce ^:private conversion-in-progress? (atom false))

(defn convert-video-to-compatible-temp-file [video-path]
  (if (compare-and-set! conversion-in-progress? false true)
    (let [temp-file (java.io.File/createTempFile "videoahmatti-converted-" ".mp4")]
      (try
        (let [result (shell/sh "ffmpeg"
                               "-y"
                               "-i" video-path
                               "-c:v" "libx264"
                               "-preset" "veryfast"
                               "-crf" "23"
                               "-c:a" "aac"
                               "-b:a" "128k"
                               "-movflags" "+faststart"
                               (.getAbsolutePath temp-file))]
          (if (zero? (:exit result))
            {:ok? true
             :file temp-file}
            (do
              (.delete temp-file)
              {:ok? false
               :error (or (:err result) "ffmpeg failed")})))
        (catch Exception e
          (.delete temp-file)
          {:ok? false
           :error (or (.getMessage e) "ffmpeg failed")})
        (finally
          (reset! conversion-in-progress? false))))
    {:ok? false
     :busy? true
     :error "video-conversion-busy"}))
