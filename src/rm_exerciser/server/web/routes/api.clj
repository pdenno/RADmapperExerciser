(ns rm-exerciser.server.web.routes.api
  (:require
   [rm-exerciser.server.web.controllers.health :as health]
   [rm-exerciser.server.web.controllers.rm-exerciser :as rm]
   [rm-exerciser.server.web.middleware.exception :as exception]
   [rm-exerciser.server.web.middleware.formats :as formats]
   [integrant.core :as ig]
   [reitit.coercion.malli :as malli]
   [reitit.ring.coercion :as coercion]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [reitit.swagger :as swagger]))

;; Routes
(defn api-routes
  "Define API routes (as opposed to page routes defined elsewhere).
   The things are examined by the swagger 2.0 API. Thus if I define a route
   here '/process-rm/:code', it will show up in swagger as /api/process-rm/{code}."
  [_opts]
  [["/swagger.json"
    {:get {:no-doc  true
           :swagger {:info {:title "rm-exerciser.server API"}}
           :handler (swagger/create-swagger-handler)}}]
   ["/health"
    {:get health/healthcheck!}]
   ["/process-rm"
    {:get {:summary "Run RADmapper code provided as a query parameter."
           :parameters {:query {:code string?
                                #_#_:data string?}} ; ToDo: Learn how to express optional query parameters.
           :handler rm/process-rm}}]])

(defn route-data
  [opts]
  (merge
    opts
    {:coercion   malli/coercion
     :muuntaja   formats/instance
     :swagger    {:id ::api}
     :middleware [;; query-params & form-params
                  parameters/parameters-middleware
                  ;; content-negotiation
                  muuntaja/format-negotiate-middleware
                  ;; encoding response body
                  muuntaja/format-response-middleware
                  ;; exception handling
                  coercion/coerce-exceptions-middleware
                  ;; decoding request body
                  muuntaja/format-request-middleware
                  ;; coercing response bodys
                  coercion/coerce-response-middleware
                  ;; coercing request parameters
                  coercion/coerce-request-middleware
                  ;; exception handling
                  exception/wrap-exception]}))

(derive :reitit.routes/api :reitit/routes)

(defmethod ig/init-key :reitit.routes/api
  [_ {:keys [base-path]
      :or   {base-path ""}
      :as   opts}]
  [base-path (route-data opts) (api-routes opts)])
