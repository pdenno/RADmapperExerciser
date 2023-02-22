(ns rm-exerciser.server.web.controllers.rm-exerciser
  (:require
   [clojure.tools.logging :as log]
   [rad-mapper.evaluate   :as ev]
   [rm-exerciser.server.web.routes.utils :as utils]
   [ring.util.http-response :as http-response])
  (:import
    [java.util Date]))

(def diag (atom nil))

;;; Great example; I'll keep it for a while.
(defn save-message!
  [{{:strs [name message]} :form-params :as request}]
  (reset! diag request)
  (log/debug "saving message" name message)
  (let [{:keys [query-fn]} (utils/route-data request)]
    (try
      (if (or (empty? name) (empty? message))
        (cond-> (http-response/found "/")
          (empty? name)
          (assoc-in [:flash :errors :name] "name is required")
          (empty? message)
          (assoc-in [:flash :errors :message] "message is required"))
        (do
          (query-fn :save-message! {:name name :message message})
          (http-response/found "/")))
      (catch Exception e
        (log/error e "failed to save message!")
        (-> (http-response/found "/")
            (assoc :flash {:errors {:unknown (.getMessage e)}}))))))

#_(defn healthcheck!
  [req]
  (http-response/ok
    {:time     (str (Date. (System/currentTimeMillis)))
     :up-since (str (Date. (.getStartTime (java.lang.management.ManagementFactory/getRuntimeMXBean))))
     :app      {:status  "up"
                :message ""}}))

;;; http://localhost:3000/process-rm?code=1%2B2
(defn process-rm
  "Run RADmapper processRM, returning the result."
  [{:keys [query-params] :as request}]
  (reset! diag request)
  (try
    (if-let [code (get query-params "code")]
      (let [res (ev/processRM :ptag/exp code {:pprint? true})]
        (http-response/ok {:status 200
                           :headers {}
                           :body res}))
      (http-response/ok {:status 400 ; "bad request"
                         :body "No code found."}))
    (catch Exception e
      (log/error e "Error processing RADmapper code. Code = " (get query-params "code"))
      (-> (http-response/found "/")
          (assoc :flash {:errors {:unknown (.getMessage e)}})))))

#_(defn process-rm-path
  "Run RADmapper processRM, returning the result."
  [{:keys [path-params] :as request}]
  (reset! diag request)
  (try
    (if-let [code (:code path-params)]
      (let [res (ev/processRM :ptag/exp code {:pprint? true})]
        (http-response/ok {:status 200
                           :headers {}
                           :body res}))
      (http-response/ok {:status 400 ; "bad request"
                         :body "No code found."}))
    (catch Exception e
      (log/error e "Error processing RADmapper code. Code = " (:code path-params))
      (-> (http-response/found "/")
          (assoc :flash {:errors {:unknown (.getMessage e)}})))))
