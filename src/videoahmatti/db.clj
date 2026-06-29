(ns videoahmatti.db
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [jsonista.core :as json]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]))

(def ^:private json-mapper
  (json/object-mapper))

(defn- read-detections [value]
  (when (and (string? value) (not (str/blank? value)))
    (json/read-value value json-mapper)))

(defn- write-detections [detections]
  (when (some? detections)
    (json/write-value-as-string detections json-mapper)))

(defn- with-parsed-detections [video]
  (if (nil? video)
    nil
    (update video :detections read-detections)))

(defn ensure-schema! [datasource]
  (if-let [schema-resource (io/resource "schema.sql")]
    (let [sql (slurp schema-resource)
          statements (->> (str/split sql #";")
                          (map str/trim)
                          (remove str/blank?))]
      (doseq [statement statements]
        (jdbc/execute! datasource [statement]))
      (log/infof "Schema initialized (%d statements)" (count statements)))
    (throw (ex-info "schema.sql not found on classpath" {}))))

(defn make-datasource [cfg]
  (jdbc/get-datasource {:jdbcUrl (get-in cfg [:db :jdbc-url])}))

(defn insert-video! [datasource {:keys [storage-path filename]}]
  (let [inserted (jdbc/execute-one!
                  datasource
                  ["insert into videos (storage_path, filename, discovered_at)
                    values (?, ?, current_timestamp)
                    on conflict(storage_path) do nothing
                    returning id"
                   storage-path
                   filename]
                  {:builder-fn rs/as-unqualified-lower-maps})]
    (when inserted
      (log/infof "New video added: %s" storage-path)
      inserted)))

(defn list-videos [datasource]
  (mapv with-parsed-detections
        (jdbc/execute! datasource
                       ["select id, storage_path, filename, detections, duration_sec, discovered_at from videos order by filename desc, id desc"]
                       {:builder-fn rs/as-unqualified-lower-maps})))

(defn find-adjacent-videos [datasource {:keys [id filename]}]
  {:previous
   (with-parsed-detections
     (first (jdbc/execute! datasource
                           ["select id, storage_path, filename, detections, duration_sec, discovered_at
                             from videos
                             where filename > ? or (filename = ? and id > ?)
                             order by filename asc, id asc
                             limit 1"
                            filename
                            filename
                            id]
                           {:builder-fn rs/as-unqualified-lower-maps})))
   :next
   (with-parsed-detections
     (first (jdbc/execute! datasource
                           ["select id, storage_path, filename, detections, duration_sec, discovered_at
                             from videos
                             where filename < ? or (filename = ? and id < ?)
                             order by filename desc, id desc
                             limit 1"
                            filename
                            filename
                            id]
                           {:builder-fn rs/as-unqualified-lower-maps})))})

(defn find-video-by-id [datasource video-id]
  (with-parsed-detections
    (first (jdbc/execute! datasource
                          ["select id, storage_path, filename, detections, duration_sec, discovered_at from videos where id = ?" video-id]
                          {:builder-fn rs/as-unqualified-lower-maps}))))

(defn find-video-by-storage-path [datasource storage-path]
  (with-parsed-detections
    (first (jdbc/execute! datasource
                          ["select id, storage_path, filename, detections, duration_sec, discovered_at from videos where storage_path = ?" storage-path]
                          {:builder-fn rs/as-unqualified-lower-maps}))))

(defn find-next-undetected-video [datasource]
  (with-parsed-detections
    (first (jdbc/execute! datasource
                          ["select id, storage_path, filename, detections, duration_sec, discovered_at
                            from videos
                            where detections is null
                            order by filename desc, id desc
                            limit 1"]
                          {:builder-fn rs/as-unqualified-lower-maps}))))

(defn list-videos-discovered-before [datasource timestamp]
  (mapv with-parsed-detections
        (jdbc/execute! datasource
                       ["select id, storage_path, filename, detections, duration_sec, discovered_at
                         from videos
                         where discovered_at < ?
                         order by id asc"
                        timestamp]
                       {:builder-fn rs/as-unqualified-lower-maps})))

(defn set-video-detections! [datasource video-id detections]
  (jdbc/execute-one!
   datasource
   ["update videos
     set detections = ?
     where id = ?"
    (write-detections detections)
    video-id]))

(defn delete-videos-by-storage-paths! [datasource storage-paths]
  (let [paths (->> storage-paths
                   (remove str/blank?)
                   distinct
                   vec)]
    (if (empty? paths)
      0
      (let [placeholders (str/join "," (repeat (count paths) "?"))
            sql (str "delete from videos where storage_path in (" placeholders ")")
            result (jdbc/execute-one! datasource (into [sql] paths))]
        (or (:next.jdbc/update-count result) 0)))))

(defn find-thumbnail [datasource video-id]
  (first (jdbc/execute! datasource ["select id, video_id, image_blob, width, height, mime_type, generated_at from thumbnails where video_id = ?" video-id] {:builder-fn rs/as-unqualified-lower-maps})))

(defn upsert-thumbnail! [datasource {:keys [video-id image-blob width height mime-type]}]
  (jdbc/execute-one!
   datasource
   ["insert into thumbnails (video_id, image_blob, width, height, mime_type, generated_at)
     values (?, ?, ?, ?, ?, current_timestamp)
     on conflict(video_id) do update set
       image_blob = excluded.image_blob,
       width = excluded.width,
       height = excluded.height,
       mime_type = excluded.mime_type,
       generated_at = current_timestamp"
    video-id
    image-blob
    width
    height
    mime-type]))
