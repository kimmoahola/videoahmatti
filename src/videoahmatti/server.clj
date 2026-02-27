(ns videoahmatti.server
	(:require
	 [clojure.tools.logging :as log]
	 [org.httpkit.server :as http]
	 [videoahmatti.routes :as routes]))

(defonce server* (atom nil))

(defn start! [cfg datasource queue-state]
  (let [host (get-in cfg [:app :host])
        port (get-in cfg [:app :port])
        handler (routes/make-handler {:config cfg
                                      :datasource datasource
                                      :queue-state queue-state})]
    (when @server*
      (@server*)
      (reset! server* nil))
    (reset! server* (http/run-server handler {:ip host :port port}))
    (log/infof "HTTP server listening on %s:%s" host port)
    @server*))

(defn stop! []
  (when @server*
    (@server*)
    (reset! server* nil)
    (log/info "HTTP server stopped")))