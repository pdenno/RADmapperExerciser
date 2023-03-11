(ns rm-exerciser.server.web.controllers.rm-exerciser
  (:require
   [clojure.string        :refer [split]]
   [clojure.tools.logging :as log]
   [clojure.walk          :as walk :refer [keywordize-keys]]
   [rad-mapper.evaluate   :as ev]
   [schema-db.core        :refer [connect-db]]
   [schema-db.resolvers   :refer [pathom-resolve]]
   [rm-exerciser.server.example-db :as examp]
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

;;; http://localhost:3000/process-rm?code=1%2B2
(defn process-rm
  "Run RADmapper processRM, returning the result."
  [{:keys [query-params] :as request}]
  (reset! diag request)
  (try
    (if-let [code (get query-params "code")]
      (let [data (or (get query-params "data") "")
            res (ev/processRM :ptag/exp code {:pprint? true :user-data data})]
        (http-response/ok {#_#_:status 200
                           #_#_:headers {}
                           :body res}))
      (http-response/ok {:status 400 ; "bad request"
                         :body "No code found."}))
    (catch Exception e

      (log/error e "Error processing RADmapper code. Code = " (get query-params "code"))
      (-> (http-response/found "/")
          (assoc :flash {:errors {:unknown (.getMessage e)}})))))

(defn post-example
  "Save an example in the examples data base."
  [request]
  (log/info "body = " (-> request :parameters :body))
  (try
    (if (-> request :parameters :body :code)
      (if-let [uuid (examp/store-example (-> request :parameters :body))]
        (http-response/ok {:save-id (str uuid)})
        (http-response/ok {:status 400 :body "Store failed."}))
      (http-response/ok {:status 400 :body "No code found."}))
    (catch Exception e
      (log/error e "Error in post-example. parameters = " (:parameters request))
      (-> (http-response/found "/")
          (assoc :flash {:errors {:unknown (.getMessage e)}})))))

;;; {:ident-type schema/name, :ident-val "urn:oagis-10.8:Nouns:Invoice", :request-objs [:sdb/schema-object]}
;;; (pathom-resolve [{[:schema/name "urn:oagis-10.8.4:Nouns:Invoice"] [:sdb/schema-object]}])
(defn graph-query
  "Make a graph query (currently only to data managed by this server).
   Query parameters:
     - ident-type   : a namespaced string such as 'schema/name'.
     - ident-val    : a string, that is the value of a lookup-id.
     - request-objs : a string of elements separated by '|' that will be keywordized to the 'sdb' ns,
                      for example, 'foo|bar' ==> [:sdb/foo :sdb/bar]."
  [request]
  (log/info "Call to graph-query")
  (if (connect-db)
    (let [{:keys [ident-type ident-val request-objs] :as req}
          (-> request
              :query-params
              keywordize-keys
              (update :ident-type keyword)
              (update :request-objs #(as-> % ?x
                                       (split ?x #"\|")
                                       (mapv (fn [x] (keyword "sdb" x)) ?x))))] ; ToDo: check for '/' in x.
      (log/info "****/api/graph-query: " req)
      (if (and ident-type ident-val request-objs)
        (let [query [{[ident-type ident-val] request-objs}]
              res (pathom-resolve query)]
          (reset! diag {:query query :res res})
          (log/info "****/api/graph-query: response = " (http-response/ok "some-string"))
          (http-response/ok {:objects res}))
        (http-response/ok {:body "Missing query args."})))
    (http-response/ok {:body "Could not connect to DB."})))
