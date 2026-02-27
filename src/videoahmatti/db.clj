(ns videoahmatti.db
  (:require
  [clojure.java.io :as io]
  [clojure.string :as str]
  [clojure.tools.logging :as log]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]))

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
  (jdbc/execute! datasource ["select id, storage_path, filename, duration_sec, discovered_at from videos order by filename desc"] {:builder-fn rs/as-unqualified-lower-maps}))

(defn find-video-by-id [datasource video-id]
  (first (jdbc/execute! datasource ["select id, storage_path, filename, duration_sec, discovered_at from videos where id = ?" video-id] {:builder-fn rs/as-unqualified-lower-maps})))

(defn find-video-by-storage-path [datasource storage-path]
  (first (jdbc/execute! datasource ["select id, storage_path, filename, duration_sec, discovered_at from videos where storage_path = ?" storage-path] {:builder-fn rs/as-unqualified-lower-maps})))

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
