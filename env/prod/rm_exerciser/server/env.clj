(ns rm-exerciser.server.env
  (:require
   [clojure.tools.logging :as log]
   #_[rm-exerciser.server.dev-middleware :refer [wrap-dev]])) ; ToDo: Doesn't belong here.

(def defaults
  {:init       (fn []
                 (log/info "\n-=[ starting]=-"))
   :started    (fn []
                 (log/info "\n-=[ started successfully]=-"))
   :stop       (fn []
                 (log/info "\n-=[ has shut down successfully]=-"))
   :middleware (fn [handler _] handler) ; ToDo: Put this back as shown.
   :opts       {:profile :dev #_:prod}})  ; ToDo: Put this back as shown.
