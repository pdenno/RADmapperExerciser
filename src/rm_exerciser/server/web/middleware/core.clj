(ns rm-exerciser.server.web.middleware.core
  (:require
   [clojure.java.io :as io]
   [mount.core :as mount :refer [defstate]]
   [ring.middleware.defaults :as defaults]
   [ring.middleware.session.cookie :as cookie]
   [taoensso.timbre :as log]))

#_(defn wrap-base
  [{:keys [_metrics site-defaults-config cookie-secret] :as opts}]
  (let [cookie-store (cookie/cookie-store {:key (.getBytes ^String cookie-secret)})]
    (fn [handler]
      (cond-> ((:middleware env/defaults) handler opts)
              true (defaults/wrap-defaults
                    (assoc-in site-defaults-config [:session :store] cookie-store))))))

(def diag (atom {}))

;;; (:middleware env/defaults) is just: (defn wrap-dev [handler opts] (-> handler ))
;;; Compare to ~/Documents/git/clojure/kit-example/guestbook/src/clj/kit/guestbook/web/middleware/core.clj
;;; (If you don't have such a thing, you can built it; it is just the 'getting started' demo for kit-clj.)
(defn wrap-base
  [{:keys [site-defaults-config cookie-secret]}]
  (reset! diag {:site-defaults-config  site-defaults-config :cookie-secret cookie-secret})
  (log/info "wrap-base: site-defaults-config = " site-defaults-config)
  (log/info "cookie-secret = " cookie-secret)
  (let [s ^String cookie-secret
        cookie-store (cookie/cookie-store {:key (.getBytes s)})]
    (log/info "cookie-store = " cookie-store)
    (fn [handler]
       (defaults/wrap-defaults handler
                               (assoc-in site-defaults-config [:session :store] cookie-store)))))

#_(defn wrap-base []
  (let [config (-> "system.edn" io/resource slurp read-string)
        site-defaults-config  (-> config :dev :handler/ring :site-defaults-config)
        cookie-secret ^String (-> config :dev :handler/ring :cookie-secret)
        cookie-store (cookie/cookie-store {:key (.getBytes cookie-secret)})]
    (fn [handler]
      (defaults/wrap-defaults handler
                              (assoc-in site-defaults-config [:session :store] cookie-store)))))

#_(defstate middleware-core
  :start (wrap-base))
