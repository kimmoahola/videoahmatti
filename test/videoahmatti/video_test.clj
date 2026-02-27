(ns videoahmatti.video-test
  (:require
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
