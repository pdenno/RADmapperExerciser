(ns rm-exerciser.server.web.controllers.health
  (:require
   [clojure.tools.logging :as log]
    [ring.util.http-response :as http-response])
  (:import
    [java.util Date]))

(defn healthcheck!
  [_req]
  (log/info "=============== Doing the health check! ===================")
  (Thread/sleep 2000)
  (http-response/ok
    {:time     (str (Date. (System/currentTimeMillis)))
     :up-since (str (Date. (.getStartTime (java.lang.management.ManagementFactory/getRuntimeMXBean))))
     :app      {:status  "up"
                :message ""}}))
