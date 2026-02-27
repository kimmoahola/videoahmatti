(ns videoahmatti.jobs
  (:require
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [videoahmatti.db :as db])
  (:import
   (java.nio.file Files)))

(def video-extensions
  #{"mp4" "mkv" "webm" "mov" "avi" "m4v"})

(defn- extension-of [filename]
  (some-> filename
          (str/split #"\.")
          last
          str/lower-case))

(defn- video-file? [path]
  (and (Files/isRegularFile path (make-array java.nio.file.LinkOption 0))
       (->> path
            .getFileName
            str
            extension-of
            (contains? video-extensions))))

(defn- walk-files [root-path]
  (with-open [stream (Files/walk root-path (make-array java.nio.file.FileVisitOption 0))]
    (doall
     (for [path (iterator-seq (.iterator stream))
           :when (video-file? path)]
       path))))

(defn latest-file-in-path [path]
  (let [root-file (io/file (str path))
        files (filter #(video-file? (.toPath %))
                      (file-seq root-file))]
    (when (seq files)
      (apply max-key
             #(.lastModified ^java.io.File %)
             files))))

(defn- file-path->db [path]
  {:storage-path (.toString path)
   :filename (str (.getFileName path))})

(defn scan-videos! [cfg datasource]
  (let [start-time (System/currentTimeMillis)
        root-path (.toPath (io/file (get-in cfg [:app :video-root])))
        latest-file (.toString (latest-file-in-path root-path))
        result (if (and latest-file
                        (not (db/find-video-by-storage-path datasource latest-file)))
                 (reduce
                  (fn [acc path]
                    (cond-> (update acc :scanned inc)
                      (db/insert-video! datasource (file-path->db path))
                      (update :inserted inc)))
                  {:scanned 0 :inserted 0}
                  (walk-files root-path))
                 {:scanned 0 :inserted 0})]
    (log/infof "Video scan completed in %d ms with result %s"
               (- (System/currentTimeMillis) start-time)
               result)
    result))

(defn start-video-scan-scheduler! [cfg datasource]
  (let [interval-seconds (get-in cfg [:jobs :scan-interval-seconds])
        interval-ms (* 1000 interval-seconds)
        stop-chan (async/chan)]
    (log/infof "Starting periodic video scan scheduler (interval=%ss)" interval-seconds)
    (scan-videos! cfg datasource)
    (async/go-loop []
      (let [[_ channel] (async/alts! [stop-chan (async/timeout interval-ms)])]
        (if (= channel stop-chan)
          (log/info "Video scan scheduler stopped")
          (do
            (try
              (scan-videos! cfg datasource)
              (catch Exception e
                (log/error e "Periodic scan failed")))
            (recur)))))
    {:stop-chan stop-chan
     :interval-seconds interval-seconds}))
