(ns rm-exerciser.server.core
  "top-most file for starting the server, sets mount state server and system atom."
  (:require
   [clojure.java.io :as io]
   [mount.core :as mount :refer [defstate]]
   [mount-up.core :as mu]
   [rad-mapper.evaluate] ; for mount
   [rad-mapper.resolvers :refer [schema-db-atm]]          ; for mount
   [rm-exerciser.server.web.handler]                      ; for mount
   [rm-exerciser.server.web.handler :refer [handler-map]] ; for mount
   [ring.adapter.undertow :refer [run-undertow]] ; either...
   ;[ring.adapter.jetty :as jetty]                ; ...or
   [taoensso.timbre :as log])
  (:gen-class))

;; log uncaught exceptions in threads
(Thread/setDefaultUncaughtExceptionHandler
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_ thread ex]
      (log/error {:what :uncaught-exception
                  :exception ex
                  :where (str "Uncaught exception on" (.getName thread))}))))

(defonce system (atom nil))

(defn stop-server [& {:keys [profile] :or {profile :dev}}]
  (.stop @system)
  (reset! system nil)
  (when (= profile :prod) (shutdown-agents)))

(defn start [handler {:keys [port] :as opts}]
  (try
    ;; Options: https://github.com/luminus-framework/ring-undertow-adapter
    (let [server (run-undertow handler opts)
          #_(jetty/run-jetty handler {:port 3000, :join? false})]
      server)
    (catch Throwable t
      (log/error t (str "server failed to start on port: " port)))))

;;; Handler of opts =  #function[clojure.lang.AFunction/1]
;;; Handler made from opts handler =  #atom[#delay[{:status :pending, :val nil} 0x769219c3] 0x1630694c]
;;; handler = #function[kit.guestbook.core/eval24494/fn--24495/fn--24498]
;;; server  = #object[io.undertow.Undertow 0x7dbbdbb0 io.undertow.Undertow@7dbbdbb0]
(defn start-server [& {:keys [profile] :or {profile :dev}}]
  (mu/on-upndown :info mu/log :before)
  (let [base-config (-> "system.edn" io/resource slurp read-string profile)
        port (-> base-config :server/http :port)
        host (-> base-config :server/http :host)]
    (try (let [handler (atom (delay (:handler/ring handler-map)))
               server (start (fn [req] (@@handler req)) {:port port :host host})]
           (reset! system server)
           (log/info "Started server (exerciser) at port" port)
           {:handler handler
            :server  server})
         (catch Throwable t
           (log/error t "Server failed to start on host " host " port " port ".")))))

(defn -main [& _]
  (start-server))

;;; This is top-most state for starting the server; it happens last.
(defstate server
  :start (start-server)
  :stop (stop-server))
