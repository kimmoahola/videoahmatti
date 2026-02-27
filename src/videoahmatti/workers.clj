(ns videoahmatti.workers
  (:require
   [clojure.java.shell :as shell])
  (:import
   (java.nio.file Files)))

(defonce ^:private conversion-in-progress? (atom false))

(defn generate-thumbnail-bytes [video-path {:keys [timestamp-sec width height]}]
  (let [temp-file (java.io.File/createTempFile "videoahmatti-thumb-" ".jpg")]
    (try
      (let [result (shell/sh "ffmpeg"
                             "-y"
                             "-ss" (str timestamp-sec)
                             "-i" video-path
                             "-vframes" "1"
                             "-vf" (str "scale=" (or width 320) ":" (or height 180) ":force_original_aspect_ratio=decrease")
                             "-q:v" "5"
                             (.getAbsolutePath temp-file))]
        (if (zero? (:exit result))
          {:ok? true
           :image-bytes (Files/readAllBytes (.toPath temp-file))
           :width (or width 320)
           :height (or height 180)
           :mime-type "image/jpeg"}
          {:ok? false
           :error (or (:err result) "ffmpeg failed")}))
      (finally
        (.delete temp-file)))))

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
