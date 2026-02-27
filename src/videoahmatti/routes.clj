(ns videoahmatti.routes
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]
   [videoahmatti.util :as util]
   [videoahmatti.video :as video]))

(defn- request-view [request]
  (let [uri (:uri request)]
    {:request request
     :method (:request-method request)
     :uri uri
     :page-id (video/parse-page-video-id uri)
     :video-route (video/parse-video-route uri)
     :video-mode (video/stream-or-download request)}))

(defn- route-home [ctx {:keys [method uri request]}]
  (when (and (= method :get) (= uri "/"))
    (video/home-page ctx request)))

(defn- route-watch-page [ctx {:keys [method page-id request]}]
  (when (and (= method :get) (some? page-id))
    (video/watch-page ctx request page-id)))

(defn- route-static [_ctx {:keys [method uri]}]
  (when (= method :get)
    (let [file (io/file (str "resources/public" uri))]
      (when (.exists file)
        {:status 200
         :headers {"cache-control" "private, max-age=31536000, immutable"
                   "content-type" (condp #(str/ends-with? %2 %1) uri
                                    ".css" "text/css"
                                    ".js" "application/javascript"
                                    ".png" "image/png"
                                    ".jpg" "image/jpeg"
                                    "text/plain")}
         :body file}))))

(defn- route-videos-list [ctx {:keys [method uri request]}]
  (when (and (= method :get) (= uri "/api/videos"))
    (video/list-videos ctx request)))

(defn- route-video-stream [ctx {:keys [method video-route video-mode request]}]
  (let [[path id] video-route]
    (when (and (= method :get) (= path "/api/videos") id (= :stream video-mode))
      (video/stream-video ctx request id))))

(defn- route-video-download [ctx {:keys [method video-route video-mode request]}]
  (let [[path id] video-route]
    (when (and (= method :get) (= path "/api/videos") id (= :download video-mode))
      (video/download-video ctx request id))))

(defn- route-video-download-compatible [ctx {:keys [method video-route video-mode request]}]
  (let [[path id] video-route]
    (when (and (= method :get) (= path "/api/videos") id (= :download-compatible video-mode))
      (video/download-video-compatible ctx request id))))

(defn- route-video-thumbnail [ctx {:keys [method video-route video-mode request]}]
  (let [[path id] video-route]
    (when (and (= method :get) (= path "/api/videos") id (= :thumbnail video-mode))
      (video/get-thumbnail ctx request id))))

(defn- route-video-detail [ctx {:keys [method video-route request]}]
  (let [[path id] video-route]
    (when (and (= method :get) (= path "/api/videos") id)
      (video/get-video ctx request id))))

(defn- route-request [ctx request]
  (let [dispatch (some-fn (partial route-home ctx)
                          (partial route-watch-page ctx)
                          (partial route-static ctx)
                          (partial route-videos-list ctx)
                          (partial route-video-stream ctx)
                          (partial route-video-download ctx)
                          (partial route-video-download-compatible ctx)
                          (partial route-video-thumbnail ctx)
                          (partial route-video-detail ctx))]
    (or (dispatch (request-view request))
        (util/json-response 404 {:error "not-found"}))))

(defn make-handler [ctx]
  (fn [request]
    (log/infof "%s %s" (str/upper-case (name (:request-method request))) (:uri request))
    (route-request ctx request)))