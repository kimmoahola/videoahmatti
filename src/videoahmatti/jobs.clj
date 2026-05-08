(ns videoahmatti.jobs
  (:require
   [clojure.core.async :as async]
   [clojure.tools.logging :as log]
   [videoahmatti.jobs.detection :as detection]
   [videoahmatti.jobs.videos-scan :as videos-scan]))

(defonce background-jobs {:scan-videos (atom false)
                          :detection (atom false)})

(defn- run-exclusive-job! [job-key trigger f]
  (let [job-atom (get background-jobs job-key)]
    (if (compare-and-set! job-atom false true)
      (async/thread
        (try
          (log/infof "Starting %s job (trigger=%s)" (name job-key) trigger)
          (f)
          (catch Exception e
            (log/error e (format "%s job failed (trigger=%s)" (name job-key) trigger)))
          (finally
            (reset! job-atom false))))
      (do
        (log/infof "Dropping %s job request (trigger=%s): job already running"
                   (name job-key)
                   trigger)
        nil))))

(defn trigger-detection-pass! [datasource trigger]
  (run-exclusive-job!
   :detection
   trigger
   (fn []
     (detection/run-detection-pass! datasource))))

(defn trigger-video-scan! [cfg datasource trigger]
  (run-exclusive-job!
   :scan-videos
   trigger
   (fn []
     (videos-scan/scan-videos! cfg datasource)
     (trigger-detection-pass! datasource :scan-finished))))

(defn start-video-scan-scheduler! [cfg datasource]
  (let [interval-seconds (get-in cfg [:jobs :scan-interval-seconds])
        interval-ms (* 1000 interval-seconds)
        stop-chan (async/chan)]
    (log/infof "Starting periodic video scan scheduler (interval=%ss)" interval-seconds)
    (trigger-video-scan! cfg datasource :startup)
    (async/go-loop []
      (let [[_ channel] (async/alts! [stop-chan (async/timeout interval-ms)])]
        (if (= channel stop-chan)
          (log/info "Video scan scheduler stopped")
          (do
            (trigger-video-scan! cfg datasource :interval)
            (recur)))))
    {:stop-chan stop-chan
     :interval-seconds interval-seconds}))
