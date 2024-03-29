(ns rm-exerciser.server.util
  "Do lowest level configuration (logging, etc.)."
  (:require
   [mount.core :as mount :refer [defstate]]
   [rad-mapper.util :as rm-util :refer [config-log]]
   [taoensso.timbre :as log]))

(defn init-util []
  (let [res {:log/config (log/set-config!
                          (-> (rm-util/config-log :info)
                              (update :min-level
                                      (fn [ml]
                                        (if (some #(contains? (first %) "rm-exerciser.*") ml)
                                          ml
                                          (into [[#{"user" "rm-exerciser.*"} :info]] ml))))))}]
    res))

(defstate util-state
  :start (init-util))
