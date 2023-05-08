(ns rm-exerciser.server.web.routes.pages
  (:require
   [mount.core :as mount :refer [defstate]]
   [rm-exerciser.server.web.middleware.exception :as exception]
   [rm-exerciser.server.web.pages.layout :as layout]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
   [taoensso.timbre :as log]))

(defn wrap-page-defaults []
  (let [error-page (layout/error-page
                     {:status 403
                      :title "Invalid anti-forgery token"})]
    #(wrap-anti-forgery % {:error-response error-page})))

(defn home [{:keys [flash] :as request}]
  (log/info "Request for home.html")
  (layout/render request "home.html" {:errors (:errors flash)}))

(defn page-route-vec [_opts]
  [["/" {:get home}]
   ["/index.html" {:get home}]])

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

(defn page-routes-init []
  (layout/init-selmer!)
  ["" (route-data {}) (page-route-vec {})])

(defstate page-routes
  :start  (page-routes-init))
