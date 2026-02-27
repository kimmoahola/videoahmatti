(ns videoahmatti.routes-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [jsonista.core :as json]
   [videoahmatti.routes :as routes]
   [videoahmatti.video :as video]))

(def mapper
  (json/object-mapper {:decode-key-fn keyword}))

(def test-ctx {:datasource ::fake})

(defn- handler []
  (routes/make-handler test-ctx))

(deftest health-route-returns-ok
  (testing "GET /health returns status ok"
    (let [response ((handler) {:request-method :get :uri "/health"})]
      (is (= 200 (:status response)))
      (is (= "application/json; charset=utf-8" (get-in response [:headers "content-type"]))))))

(deftest unknown-route-returns-not-found
  (testing "Unknown route returns 404"
    (let [response ((handler) {:request-method :get :uri "/missing"})]
      (is (= 404 (:status response)))
      (is (= {:error "not-found"} (json/read-value (:body response) mapper))))))

(deftest watch-page-route-dispatches
  (testing "GET /videos/:id dispatches to watch-page"
    (let [called-id (atom nil)
          watch-response {:status 200 :body "watch"}]
      (with-redefs [video/watch-page (fn [_ctx _request id]
                                       (reset! called-id id)
                                       watch-response)]
        (let [response ((handler) {:request-method :get :uri "/videos/42"})]
          (is (= 42 @called-id))
          (is (= watch-response response)))))))

(deftest stream-route-dispatches
  (testing "GET /api/videos/:id/stream dispatches to stream-video"
    (let [called-id (atom nil)
          stream-response {:status 200 :body "stream"}]
      (with-redefs [video/stream-video (fn [_ctx _request id]
                                         (reset! called-id id)
                                         stream-response)]
        (let [response ((handler) {:request-method :get :uri "/api/videos/7/stream"})]
          (is (= 7 @called-id))
          (is (= stream-response response)))))))

(deftest compatible-download-route-dispatches
  (testing "GET /api/videos/:id/download-compatible dispatches to download-video-compatible"
    (let [called-id (atom nil)
          download-response {:status 200 :body "compatible-download"}]
      (with-redefs [video/download-video-compatible (fn [_ctx _request id]
                                                      (reset! called-id id)
                                                      download-response)]
        (let [response ((handler) {:request-method :get :uri "/api/videos/17/download-compatible"})]
          (is (= 17 @called-id))
          (is (= download-response response)))))))
