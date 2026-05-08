(ns videoahmatti.video-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer [deftest is testing]]
   [jsonista.core :as json]
   [videoahmatti.db :as db]
   [videoahmatti.video :as video]
   [videoahmatti.workers :as workers]))

(def mapper
  (json/object-mapper {:decode-key-fn keyword}))

(deftest download-video-compatible-returns-busy-when-conversion-in-progress
  (testing "Returns 429 when conversion helper reports busy"
    (with-redefs [db/find-video-by-id (fn [_datasource _id]
                                        {:id 1
                                         :filename "camera_00_20260222075326.mp4"
                                         :storage_path "/tmp/fake.mp4"})
                  workers/convert-video-to-compatible-temp-file (fn [_video-path]
                                                                  {:ok? false
                                                                   :busy? true
                                                                   :error "video-conversion-busy"})]
      (let [response (video/download-video-compatible {:datasource ::fake} {} 1)
            body (json/read-value (:body response) mapper)]
        (is (= 429 (:status response)))
        (is (= {:error "video-conversion-busy"} body))))))

(deftest watch-page-renders-navigation-buttons-when-neighbors-exist
  (testing "Watch page includes previous and next links when adjacent videos exist"
    (with-redefs [db/find-video-by-id (fn [_datasource _id]
                                        {:id 10
                                         :filename "camera_00_20260222075326.mp4"
                                         :storage_path "/tmp/current.mp4"
                                         :detections [["vehicle" 0.9838]]})
                  db/find-adjacent-videos (fn [_datasource _video]
                                            {:previous {:id 11
                                                        :filename "camera_00_20260222080000.mp4"}
                                             :next {:id 9
                                                    :filename "camera_00_20260222070000.mp4"}})]
      (let [response (video/watch-page {:datasource ::fake} {} 10)
            body (:body response)]
        (is (= 200 (:status response)))
        (is (string/includes? body "href=\"/videos/11\">Previous</a>"))
        (is (string/includes? body "href=\"/videos/9\">Next</a>"))
        (is (string/includes? body "href=\"/\">Back to list</a>"))))))

(deftest watch-page-hides-missing-navigation-buttons
  (testing "Watch page renders disabled previous and next buttons when adjacent videos do not exist"
    (with-redefs [db/find-video-by-id (fn [_datasource _id]
                                        {:id 10
                                         :filename "camera_00_20260222075326.mp4"
                                         :storage_path "/tmp/current.mp4"
                                         :detections nil})
                  db/find-adjacent-videos (fn [_datasource _video]
                                            {:previous nil
                                             :next nil})]
      (let [response (video/watch-page {:datasource ::fake} {} 10)
            body (:body response)]
        (is (= 200 (:status response)))
        (is (string/includes? body "class=\"secondary disabled\" aria-disabled=\"true\">Previous</a>"))
        (is (string/includes? body "class=\"secondary disabled\" aria-disabled=\"true\">Next</a>"))
        (is (not (string/includes? body "href=\"/videos/")))))))

(deftest format-detections-selects-top-and-high-confidence
  (testing "Always keeps highest-score detection and adds all >= 0.6"
    (is (= ["vehicle (0.98)"
            "white-tailed deer (0.98)"
            "bobcat (0.82)"]
           (video/format-detections [["mule deer" 0.0896]
                                     ["vehicle" 0.9838]
                                     ["white-tailed deer" 0.982]
                                     ["bobcat" 0.8206]
                                     ["red deer" 0.045]])))
    (is (= ["red fox (0.59)"]
           (video/format-detections [["red deer" 0.045]
                                     ["red fox" 0.59]]))))

  (testing "Returns empty vector when no valid detections exist"
    (is (= []
           (video/format-detections [])))
    (is (= []
           (video/format-detections [[nil 0.9]
                                     ["bad-score" nil]
                                     ["not-a-number" "0.8"]]))))

  (testing "Does not duplicate first detection if it is also high-confidence"
    (is (= ["vehicle (0.98)"]
           (video/format-detections [["vehicle" 0.9838]
                                     ["red deer" 0.045]])))))
