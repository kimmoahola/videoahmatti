(ns videoahmatti.config
  (:require
  [aero.core :as aero]
  [clojure.java.io :as io]))

(defn load-config
  ([] (load-config "config.edn"))
  ([resource-name]
    (aero/read-config (io/resource resource-name))))
