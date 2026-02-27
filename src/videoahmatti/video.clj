(ns videoahmatti.video
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [selmer.parser :as selmer]
   [videoahmatti.db :as db]
   [videoahmatti.workers :as workers]
   [videoahmatti.util :as util]
   [videoahmatti.validation :as validation]))

(defn extract-datetime-from-filename [filename]
  (when-let [match (re-find #"(\d{14})" filename)]
    (second match)))

(defn format-datetime-string
  "Convert YYYYMMDDHHMMSS to DD.MM.YYYY klo HH:MM:SS"
  [datetime-str]
  (when datetime-str
    (let [year (subs datetime-str 0 4)
          month (subs datetime-str 4 6)
          day (subs datetime-str 6 8)
          hour (subs datetime-str 8 10)
          minute (subs datetime-str 10 12)
          second (subs datetime-str 12 14)]
      (str day "." month "." year " klo " hour ":" minute ":" second))))

(defn extract-camera-name
  "Extract camera name from filename, e.g. 'Parkkipaikka' from 'Parkkipaikka_00_20260222075326.mp4'"
  [filename]
  (when-let [match (re-find #"^([a-zA-Z0-9]+)_\d+_\d{14}" filename)]
    (second match)))

(defn extract-formatted-filename
  "Format filename to 'Camera Name DD.MM.YYYY klo HH:MM:SS'"
  [filename]
  (if-let [datetime (extract-datetime-from-filename filename)]
    (let [camera (extract-camera-name filename)
          formatted-datetime (format-datetime-string datetime)]
      (if camera
        (str camera " " formatted-datetime)
        formatted-datetime))
    (util/html-escape filename)))

(defn parse-year-month [datetime-str]
  (when datetime-str
    (str (subs datetime-str 0 4) "-" (subs datetime-str 4 6))))

(defn month-year-label [year-month]
  (if (= year-month "unknown")
    "Unknown date"
    (let [[year month] (str/split year-month #"-")
          month-names ["" "January" "February" "March" "April" "May" "June"
                       "July" "August" "September" "October" "November" "December"]
          month-idx (parse-long month)]
      (str (get month-names month-idx) " " year))))

(defn group-videos-by-month [videos]
  (let [grouped (group-by (fn [{:keys [filename]}]
                            (or (parse-year-month (extract-datetime-from-filename filename))
                                "unknown"))
                          videos)]
    (into (sorted-map-by (fn [a b] (compare b a))) grouped)))

(defn parse-page-video-id [uri]
  (when-let [[_ id] (re-matches #"/videos/([0-9]+)(?:/)?" uri)]
    (parse-long id)))

(defn prepare-videos-for-home [videos]
  (let [grouped (group-videos-by-month videos)]
    (mapv (fn [[year-month video-list]]
            {:month_label (month-year-label year-month)
             :videos (mapv (fn [video]
                             (assoc video :display_filename (extract-formatted-filename (:filename video))))
                           video-list)})
          grouped)))

(defn home-page [{:keys [datasource]} _request]
  (let [videos (db/list-videos datasource)
        grouped-data (prepare-videos-for-home videos)
        html (selmer/render-file "templates/home.html" {:grouped grouped-data})]
    (util/html-response 200 html)))

(defn watch-page [{:keys [datasource]} _request raw-id]
  (if (validation/valid-video-id? raw-id)
    (if-let [video (db/find-video-by-id datasource raw-id)]
      (let [display-filename (extract-formatted-filename (:filename video))
            html (selmer/render-file "templates/watch.html" 
                                     (assoc video :display_filename display-filename))]
        (util/html-response 200 html))
      (util/text-response 404 "video-not-found"))
    (util/text-response 400 "invalid-video-id")))

(defn parse-video-route [uri]
  (cond
    (re-matches #"/api/videos/([0-9]+)/thumbnail(?:/)?" uri)
    ["/api/videos" (parse-long (second (re-find #"/api/videos/([0-9]+)/thumbnail(?:/)?" uri)))]

    (re-matches #"/api/videos/([0-9]+)/stream(?:/)?" uri)
    ["/api/videos" (parse-long (second (re-find #"/api/videos/([0-9]+)/stream(?:/)?" uri)))]

    (re-matches #"/api/videos/([0-9]+)/download(?:/)?" uri)
    ["/api/videos" (parse-long (second (re-find #"/api/videos/([0-9]+)/download(?:/)?" uri)))]

    (re-matches #"/api/videos/([0-9]+)/download-compatible(?:/)?" uri)
    ["/api/videos" (parse-long (second (re-find #"/api/videos/([0-9]+)/download-compatible(?:/)?" uri)))]

    (re-matches #"/api/videos/([0-9]+)(?:/)?" uri)
    ["/api/videos" (parse-long (second (re-find #"/api/videos/([0-9]+)(?:/)?" uri)))]

    :else
    [uri nil]))

(defn stream-or-download [request]
  (let [uri (:uri request)]
    (cond
      (re-find #"/stream/?$" uri) :stream
      (re-find #"/download-compatible/?$" uri) :download-compatible
      (re-find #"/download/?$" uri) :download
      (re-find #"/thumbnail/?$" uri) :thumbnail
      :else :detail)))

(defn list-videos [{:keys [datasource]} _request]
  (util/json-response 200 {:videos (db/list-videos datasource)}))

(defn get-video [{:keys [datasource]} _request raw-id]
  (if (validation/valid-video-id? raw-id)
    (if-let [video (db/find-video-by-id datasource raw-id)]
      (util/json-response 200 {:video video})
      (util/json-response 404 {:error "video-not-found"}))
    (util/json-response 400 {:error "invalid-video-id"})))

(defn stream-video [{:keys [datasource]} _request raw-id]
  (if (validation/valid-video-id? raw-id)
    (if-let [video (db/find-video-by-id datasource raw-id)]
      {:status 200
       :headers {"content-type" "video/mp4"}
       :body (io/input-stream (:storage_path video))}
      (util/json-response 404 {:error "video-not-found"}))
    (util/json-response 400 {:error "invalid-video-id"})))

(defn download-video [{:keys [datasource]} _request raw-id]
  (if (validation/valid-video-id? raw-id)
    (if-let [video (db/find-video-by-id datasource raw-id)]
      {:status 200
       :headers {"content-type" "application/octet-stream"
                 "content-disposition" (str "attachment; filename=\"" (:filename video) "\"")}
       :body (io/input-stream (:storage_path video))}
      (util/json-response 404 {:error "video-not-found"}))
    (util/json-response 400 {:error "invalid-video-id"})))

(defn- compatible-download-filename [filename]
  (if (str/ends-with? (str/lower-case filename) ".mp4")
    (str (subs filename 0 (- (count filename) 4)) "-h264.mp4")
    (str filename "-h264.mp4")))

(defn- temp-file-input-stream
  "Wraps a file input stream and deletes the temp file when the stream is closed."
  [file]
  (let [stream (io/input-stream file)]
    (proxy [java.io.FilterInputStream] [stream]
      (close []
        (try
          (proxy-super close)
          (finally
            (.delete file)))))))

(defn download-video-compatible [{:keys [datasource]} _request raw-id]
  (if (validation/valid-video-id? raw-id)
    (if-let [video (db/find-video-by-id datasource raw-id)]
      (let [conversion (workers/convert-video-to-compatible-temp-file (:storage_path video))]
        (if (:ok? conversion)
          {:status 200
           :headers {"content-type" "video/mp4"
                     "content-disposition" (str "attachment; filename=\""
                                                 (compatible-download-filename (:filename video))
                                                 "\"")}
           :body (temp-file-input-stream (:file conversion))}
          (if (:busy? conversion)
            (util/json-response 429 {:error "video-conversion-busy"})
            (util/json-response 500 {:error "video-conversion-failed"}))))
      (util/json-response 404 {:error "video-not-found"}))
    (util/json-response 400 {:error "invalid-video-id"})))

(defn get-or-generate-thumbnail [datasource video-id]
  (if-let [thumbnail (db/find-thumbnail datasource video-id)]
    thumbnail
    (when-let [video (db/find-video-by-id datasource video-id)]
      (let [result (workers/generate-thumbnail-bytes (:storage_path video)
                                                     {:timestamp-sec 8
                                                      :width 320
                                                      :height 180})]
        (when (:ok? result)
          (db/upsert-thumbnail! datasource {:video-id video-id
                                            :image-blob (:image-bytes result)
                                            :width (:width result)
                                            :height (:height result)
                                            :mime-type (:mime-type result)})
          (db/find-thumbnail datasource video-id))))))


(defn get-thumbnail [{:keys [datasource]} _request raw-id]
  (if (validation/valid-video-id? raw-id)
    (let [thumbnail (get-or-generate-thumbnail datasource raw-id)]
      (if thumbnail
        {:status 200
         :headers {"content-type" (or (:mime_type thumbnail) "image/jpeg")
                   "cache-control" "private, max-age=31536000, immutable"}
         :body (java.io.ByteArrayInputStream. (:image_blob thumbnail))}
        (util/json-response 500 {:error "thumbnail-generation-failed"})))
    (util/json-response 400 {:error "invalid-video-id"})))
