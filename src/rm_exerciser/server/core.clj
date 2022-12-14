(ns rm-exerciser.server.core
  (:require
    [clojure.tools.logging :as log]
    [integrant.core :as ig]
    [rm-exerciser.server.config :as config]
    [rm-exerciser.server.env :refer [defaults]]

    ;; Edges
    [kit.edge.server.undertow]
    [rm-exerciser.server.web.handler]

    ;; Routes
    [rm-exerciser.server.web.routes.api]
    [rm-exerciser.server.web.routes.pages])
  (:gen-class))

;; log uncaught exceptions in threads
(Thread/setDefaultUncaughtExceptionHandler
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_ thread ex]
      (log/error {:what :uncaught-exception
                  :exception ex
                  :where (str "Uncaught exception on" (.getName thread))}))))

(defonce system (atom nil))

(defn stop-app []
  ((or (:stop defaults) (fn [])))
  (some-> (deref system) (ig/halt!))
  (shutdown-agents))

(defn start-app [& [params]]
  ((or (:start params) (:start defaults) (fn [])))
  (->> (config/system-config (or (:opts params) (:opts defaults) {}))
       (ig/prep)
       (ig/init)
       (reset! system))
    (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app)))

#_(defn start-app [& [params]]
  ((or (:start params) (:start defaults) (fn [])))
  (println "before system config")
  (let [sys (config/system-config (or (:opts params) (:opts defaults) {}))] ; <========== This is where the problem is!
    (println "after system config")
    (log/info "after sys config")
    (as-> sys ?sys
      (do (log/info "before prep") (ig/prep ?sys))
      (do (log/info "before init") (ig/init ?sys))
    (reset! system ?sys)))
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app)))

(defn -main [& _]
  (start-app))
