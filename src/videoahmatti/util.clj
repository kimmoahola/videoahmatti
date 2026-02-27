(ns videoahmatti.util
  (:require
  [clojure.string :as str]
  [jsonista.core :as json]))

(def mapper
  (json/object-mapper {:decode-key-fn keyword}))

(defn json-response [status body]
  {:status status
   :headers {"content-type" "application/json; charset=utf-8"}
   :body (json/write-value-as-string body mapper)})

(defn text-response [status body]
  {:status status
   :headers {"content-type" "text/plain; charset=utf-8"}
   :body body})

(defn html-response [status body]
  {:status status
   :headers {"content-type" "text/html; charset=utf-8"}
   :body body})

(defn html-escape [value]
  (str/escape (str value) {\& "&amp;" \< "&lt;" \> "&gt;" \" "&quot;" \' "&#39;"}))
