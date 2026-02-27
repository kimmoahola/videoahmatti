(ns videoahmatti.main
	(:gen-class)
	(:require
	 [clojure.tools.logging :as log]
	 [videoahmatti.config :as config]
	 [videoahmatti.db :as db]
	 [videoahmatti.jobs :as jobs]
	 [videoahmatti.server :as server]))

(defn -main [& _args]
  (let [cfg (config/load-config)
        datasource (db/make-datasource cfg)]
    (db/ensure-schema! datasource)
    (jobs/start-video-scan-scheduler! cfg datasource)
    (server/start! cfg datasource)
    (log/info "Videoahmatti started")))