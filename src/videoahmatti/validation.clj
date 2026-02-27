(ns videoahmatti.validation
  (:require
   [malli.core :as m]))

(def video-id-schema [:and int? [:> 0]])

(def tag-schema
  [:map
   [:tag string?]
   [:confidence [:double {:min 0.0 :max 1.0}]]])

(defn valid-video-id? [value]
  (m/validate video-id-schema value))
