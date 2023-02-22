(ns rm-exerciser.server.web.routes.pages
  (:require
    [rm-exerciser.server.web.middleware.exception :as exception]
    [rm-exerciser.server.web.pages.layout :as layout]
    [integrant.core :as ig]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [reitit.ring.middleware.parameters :as parameters]
    [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
    [rm-exerciser.server.web.routes.utils :as utils]
    [rm-exerciser.server.web.controllers.rm-exerciser :as rm-exerciser]))

(defn wrap-page-defaults []
  (let [error-page (layout/error-page
                     {:status 403
                      :title "Invalid anti-forgery token"})]
    #(wrap-anti-forgery % {:error-response error-page})))

(defn home [{:keys [flash] :as request}]
  (layout/render request "home.html" {:errors (:errors flash)}))

(defn page-routes [_opts]
  [["/" {:get home}]
   ["/save-message" {:post rm-exerciser/save-message!}]
   #_["/process-rm" {:get rm-exerciser/process-rm}]]) ; It only belongs in routes/api.clj ???

(defn route-data [opts]
  (merge
   opts
   {:middleware
    [;; Default middleware for pages
     (wrap-page-defaults)
     ;; query-params & form-params
     parameters/parameters-middleware
     ;; encoding response body
     muuntaja/format-response-middleware
     ;; exception handling
     exception/wrap-exception]}))

(derive :reitit.routes/pages :reitit/routes)

(defmethod ig/init-key :reitit.routes/pages
  [_ {:keys [base-path]
      :or   {base-path ""}
      :as   opts}]
  (layout/init-selmer!)
  [base-path (route-data opts) (page-routes opts)])
