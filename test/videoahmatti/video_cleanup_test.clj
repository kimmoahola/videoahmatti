(ns videoahmatti.video-cleanup-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [videoahmatti.db :as db]
   [videoahmatti.video-cleanup :as video-cleanup])
  (:import
   [java.nio.file Files]))

(deftest deletable-video-predicate
  (testing "Allows only human and/or vehicle labels"
    (is (true? (video-cleanup/deletable-video? {:detections [["human" 0.91]]})))
    (is (true? (video-cleanup/deletable-video? {:detections [["vehicle" 0.99]
                                                             ["human" 0.81]]})))
    (is (false? (video-cleanup/deletable-video? {:detections [["vehicle" 0.99]
                                                              ["fox" 0.8]]})))
    (is (false? (video-cleanup/deletable-video? {:detections []})))
    (is (false? (video-cleanup/deletable-video? {:detections nil})))))

(deftest cleanup-lists-only-matching-video-files
  (let [tmp-dir (Files/createTempDirectory "videoahmatti-cleanup-test"
                                           (make-array java.nio.file.attribute.FileAttribute 0))
        dir-file (.toFile tmp-dir)
        video-file (io/file dir-file "camera_00_20200101010101.mp4")
        sibling-jpg (io/file dir-file "camera_00_20200101010101.jpg")
        old-jpg-file (io/file dir-file "old-capture.jpg")
        sibling-json (io/file dir-file "camera_00_20200101010101.json")
        old-log-file (io/file dir-file "camera_00_20200101010101.log")
        recent-txt-file (io/file dir-file "recent.txt")
        recent-file (io/file dir-file "camera_00_20990101010101.mp4")
        untouched-file (io/file dir-file "video_999.mp4")
        deleted-paths* (atom nil)]
    (spit video-file "video")
    (spit sibling-jpg "thumb")
    (spit old-jpg-file "old-thumb")
    (spit sibling-json "meta")
    (spit old-log-file "old-log")
    (spit recent-txt-file "recent")
    (spit recent-file "new")
    (spit untouched-file "keep")
    (.setLastModified old-jpg-file 1)
    (.setLastModified old-log-file 1)
    (.setLastModified recent-txt-file (System/currentTimeMillis))
    (try
      (with-redefs [db/list-videos (fn [_datasource]
                                     [{:id 1
                                       :filename (.getName video-file)
                                       :storage_path (.getAbsolutePath video-file)
                                       :detections [["vehicle" 0.95]]}
                                      {:id 2
                                       :filename (.getName recent-file)
                                       :storage_path (.getAbsolutePath recent-file)
                                       :detections [["vehicle" 0.95]]}])
                    db/delete-videos-by-storage-paths! (fn [_datasource paths]
                                                         (reset! deleted-paths* (set paths))
                                                         (count paths))]
        (let [result (video-cleanup/delete-old-videos! {:app {:video-root (.getAbsolutePath dir-file)}} ::fake)]
          (is (false? (:dry-run result)))
          (is (= 2 (count (:files-to-delete result))))
          (is (= 1 (:video-files-to-delete result)))
          (is (= 1 (:non-video-files-to-delete result)))
          (is (= 2 (:deleted-files result)))
          (is (= 2 (:deleted-rows result)))
          (is (empty? (:failed-files result)))
          (is (= #{(.getAbsolutePath video-file)
                   (.getAbsolutePath old-jpg-file)}
                 @deleted-paths*))
          (is (= #{(.getAbsolutePath video-file)
                   (.getAbsolutePath old-jpg-file)}
                 (set (:files-to-delete result))))
          (is (false? (.exists video-file)))
          (is (true? (.exists sibling-jpg)))
          (is (false? (.exists old-jpg-file)))
          (is (true? (.exists sibling-json)))
          (is (true? (.exists old-log-file)))
          (is (true? (.exists recent-txt-file)))
          (is (true? (.exists recent-file)))
          (is (true? (.exists untouched-file)))))
      (finally
        (.delete video-file)
        (.delete sibling-jpg)
        (.delete old-jpg-file)
        (.delete sibling-json)
        (.delete old-log-file)
        (.delete recent-txt-file)
        (.delete recent-file)
        (.delete untouched-file)
        (.delete dir-file)))))
