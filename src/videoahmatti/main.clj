(ns videoahmatti.main
  (:gen-class)
  (:require
   [clojure.tools.logging :as log]
   [videoahmatti.config :as config]
   [videoahmatti.db :as db]
   [videoahmatti.background-jobs :as background-jobs]
   [videoahmatti.server :as server]))

(defn -main [& _args]
  (let [cfg (config/load-config)
        datasource (db/make-datasource cfg)]
    (db/ensure-schema! datasource)
    (server/start! cfg datasource)
    (background-jobs/start-video-cleanup-scheduler! cfg datasource)
    (background-jobs/start-video-scan-scheduler! cfg datasource)
    (log/info "Videoahmatti started")))
