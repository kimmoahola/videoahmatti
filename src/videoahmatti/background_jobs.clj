(ns videoahmatti.background-jobs
  (:require
   [clojure.core.async :as async]
   [clojure.tools.logging :as log]
   [videoahmatti.detection :as detection]
   [videoahmatti.video-cleanup :as video-cleanup]
   [videoahmatti.videos-scan :as videos-scan]))

(defonce background-jobs {:scan-videos (atom false)
                          :detection (atom false)
                          :cleanup (atom false)})

(defn- run-exclusive-job! [job-key trigger f]
  (let [job-atom (get background-jobs job-key)]
    (if (compare-and-set! job-atom false true)
      (async/thread
        (try
          #_(log/infof "Starting %s job (trigger=%s)" (name job-key) trigger)
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

(defn trigger-video-cleanup! [cfg datasource trigger]
  (run-exclusive-job!
   :cleanup
   trigger
   (fn []
     (log/infof "trigger-video-cleanup! result (trigger=%s): %s" trigger
                (video-cleanup/delete-old-videos! cfg datasource)))))

(defn ensure-python-deps!
  "Ensures that the Python dependencies are installed.
   The deps are installed here instead of the Dockerfile to greatly reduce docker image size."
  []
  (let [venv-path "venv"
        pip-path (str venv-path "/bin/pip")]
    (when-not (.exists (java.io.File. pip-path))
      (log/info "Python virtual environment not found, creating...")
      (let [process (-> (ProcessBuilder. ["python3" "-m" "venv" venv-path])
                        (.redirectErrorStream true)
                        (.start))]
        (.waitFor process)
        (log/info "Python virtual environment created")))
    (log/info "Installing Python dependencies...")
    (let [process (-> (ProcessBuilder. [pip-path "install" "--no-cache-dir"
                                        "--disable-pip-version-check"
                                        "-r" "requirements.txt"])
                      (.redirectErrorStream true)
                      (.start))]
      (.waitFor process)
      (log/info "Python dependencies installed"))))

(defn start-video-scan-scheduler! [cfg datasource]
  (let [interval-seconds (get-in cfg [:jobs :scan-interval-seconds])
        interval-ms (* 1000 interval-seconds)
        stop-chan (async/chan)]
    (ensure-python-deps!)
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

(defn start-video-cleanup-scheduler! [cfg datasource]
  (let [interval-seconds (get-in cfg [:jobs :cleanup-interval-seconds] 86400)
        interval-ms (* 1000 interval-seconds)
        stop-chan (async/chan)]
    (log/infof "Starting periodic video cleanup scheduler (interval=%ss)" interval-seconds)
    (trigger-video-cleanup! cfg datasource :startup)
    (async/go-loop []
      (let [[_ channel] (async/alts! [stop-chan (async/timeout interval-ms)])]
        (if (= channel stop-chan)
          (log/info "Video cleanup scheduler stopped")
          (do
            (trigger-video-cleanup! cfg datasource :interval)
            (recur)))))
    {:stop-chan stop-chan
     :interval-seconds interval-seconds}))
