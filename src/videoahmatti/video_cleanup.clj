(ns videoahmatti.video-cleanup
  (:require
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [videoahmatti.db :as db]
   [videoahmatti.videos-scan :as videos-scan])
  (:import
   [java.nio.file Files]
   [java.time Instant ZoneOffset ZonedDateTime]
   [java.time.format DateTimeFormatter]))

(def ^:private cleanup-labels #{"human" "vehicle"})

(def ^:private removable-extensions
  (conj videos-scan/video-extensions "jpg"))

(def ^:private filename-timestamp-formatter
  (DateTimeFormatter/ofPattern "yyyyMMddHHmmss"))

(defn- six-month-cutoff-filename-timestamp []
  (.format filename-timestamp-formatter
           (.minusMonths (ZonedDateTime/now ZoneOffset/UTC) 6)))

(defn- six-month-cutoff-instant []
  (.toInstant (.minusMonths (ZonedDateTime/now ZoneOffset/UTC) 6)))

(defn- extract-filename-timestamp [filename]
  (when (string? filename)
    (second (re-find #"(\d{14})" filename))))

(defn- old-video-by-filename? [cutoff-timestamp video]
  (when-let [video-ts (extract-filename-timestamp (:filename video))]
    (neg? (compare video-ts cutoff-timestamp))))

(defn- extension-of [filename]
  (some-> filename
          (str/split #"\.")
          last
          str/lower-case))

(defn- old-non-video-file? [cutoff-instant file]
  (let [filename (.getName ^java.io.File file)
        extension (extension-of filename)
        modified-inst (-> file
                          .toPath
                          (Files/getLastModifiedTime (make-array java.nio.file.LinkOption 0))
                          .toInstant)]
    (and (.isFile ^java.io.File file)
         (= "jpg" extension)
         (.isBefore ^Instant modified-inst cutoff-instant))))

(defn- list-old-non-video-files [video-root cutoff-instant]
  (let [root-file (io/file video-root)]
    (if (.exists root-file)
      (->> (file-seq root-file)
           (filter #(old-non-video-file? cutoff-instant %))
           (map #(.getAbsolutePath ^java.io.File %))
           vec)
      [])))

(defn deletable-video? [video]
  (let [labels (->> (:detections video)
                    (keep (fn [entry]
                            (let [label (first entry)]
                              (when (string? label)
                                (str/lower-case label)))))
                    set)]
    (boolean
     (and (seq labels)
          (set/subset? labels cleanup-labels)))))

(defn- delete-file! [path]
  (try
    (if (Files/deleteIfExists (.toPath (java.io.File. path)))
      {:path path :deleted? true}
      {:path path :deleted? false})
    (catch Exception e
      {:path path
       :deleted? false
       :error (or (.getMessage e) "delete-failed")})))

(defn delete-old-videos! [cfg datasource]
  (let [dry-run false
        cutoff-filename (six-month-cutoff-filename-timestamp)
        cutoff-instant (six-month-cutoff-instant)
        candidates (->> (db/list-videos datasource)
                        (filter #(old-video-by-filename? cutoff-filename %))
                        (filter deletable-video?))
        video-files-to-delete (->> candidates
                                   (map :storage_path)
                                   distinct
                                   vec)
        non-video-files-to-delete (list-old-non-video-files (get-in cfg [:app :video-root]) cutoff-instant)
        files-to-delete (->> (concat video-files-to-delete non-video-files-to-delete)
                             (filter (fn [path]
                                       (contains? removable-extensions (extension-of path))))
                             distinct
                             sort
                             vec)
        delete-results (if dry-run
                         (mapv (fn [path]
                                 {:path path
                                  :deleted? false
                                  :dry-run? true})
                               files-to-delete)
                         (mapv delete-file! files-to-delete))
        deleted-paths (->> delete-results
                           (filter :deleted?)
                           (mapv :path))
        delete-db-count (if dry-run
                          0
                          (db/delete-videos-by-storage-paths! datasource deleted-paths))
        failed-deletes (->> delete-results
                            (remove #(or (:deleted? %) (:dry-run? %)))
                            (mapv #(select-keys % [:path :error])))]
    (if dry-run
      (log/infof "Video cleanup dry-run: files-to-delete=%d"
                 (count files-to-delete))
      (when (or (seq deleted-paths) (seq failed-deletes))
        (log/infof "Video cleanup finished: deleted-files=%d deleted-rows=%d failed-files=%d"
                   (count deleted-paths)
                   delete-db-count
                   (count failed-deletes))))
    {:checked-videos (count candidates)
     :dry-run dry-run
     :files-to-delete files-to-delete
     :video-files-to-delete (count video-files-to-delete)
     :non-video-files-to-delete (count non-video-files-to-delete)
     :deleted-files (count deleted-paths)
     :deleted-rows delete-db-count
     :failed-files failed-deletes
     :cutoff cutoff-filename}))
