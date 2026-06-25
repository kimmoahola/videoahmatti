(ns videoahmatti.thumbnail
  (:require
   [clojure.java.shell :as shell]
   [clojure.tools.logging :as log]
   [videoahmatti.db :as db])
  (:import
   [java.nio.file Files]))

(defn generate-thumbnail-bytes [video-path {:keys [timestamp-sec width height]}]
  (log/infof "generate-thumbnail-bytes: video-path=%s" video-path)
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

(defn get-or-generate-thumbnail! [datasource video-id]
  (if-let [thumbnail (db/find-thumbnail datasource video-id)]
    thumbnail
    (if-let [video (db/find-video-by-id datasource video-id)]
      (let [result (generate-thumbnail-bytes (:storage_path video)
                                             {:timestamp-sec 8
                                              :width 320
                                              :height 180})]
        (when (:ok? result)
          (db/upsert-thumbnail! datasource {:video-id video-id
                                            :image-blob (:image-bytes result)
                                            :width (:width result)
                                            :height (:height result)
                                            :mime-type (:mime-type result)})
          (db/find-thumbnail datasource video-id)))
      nil)))
