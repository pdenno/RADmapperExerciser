(ns rm-exerciser.app.components.save-modal
  (:require
   [ajax.core :refer [GET POST]]
   [applied-science.js-interop :as j]
   [cemerick.url               :as url]
   [clojure.pprint             :refer [cl-format]]
   [clojure.walk               :refer [keywordize-keys]]
   [helix.core :refer [defnc $]]
   [helix.hooks :as hooks]
   [promesa.core :as p]
   ["@mui/material/Box$default" :as Box]
   ["@mui/material/IconButton$default" :as IconButton]
   ["@mui/icons-material/Save$default" :as Save]
   ["@mui/material/Typography$default" :as Typography]
   ["@mui/material/Modal$default" :as Modal]))

(def style (clj->js
            {:position "absolute", ; as 'absolute'
             :top "50%",
             :left "50%",
             :transform "translate(-50%, -50%)",
             :width 650,
             :bgcolor "background.paper",
             :border "2px solid #000",
             :boxShadow 24,
             :p 2}))

(def white-style (clj->js {:color "background.paper"}))
(def diag (atom nil))
(def response-atm (atom nil))

;;; ToDo: This seems excessively complex, but it took me a while just to get this far!
(defn positive-response!
  "Set the response atm and resolve the promise"
  [response p]
  (js/console.log (str "CLJS-AJAX returns:" response))
  (reset! response-atm response)
  (p/resolve! p))

(defn error-response!
  [status status-text p]
  (js/console.log (str "CLJS-AJAX error: status= " status " status-text= " status-text))
  (p/resolve! p))

(defn handler [response]
  (js/console.log (str "CLJS-AJAX returns:" response))
  (reset! response-atm response)
  response)

(defn error-handler [{:keys [status status-text]}]
  (.log js/console (str "CLJS-AJAX error: status= " status " status-text= " status-text)))

;;; https://mui.com/material-ui/react-modal/
(defnc SaveModal [{:keys [code-fn data-fn]}]
  (let [[open, set-open] (hooks/use-state false)
        [_url set-url] (hooks/use-state nil)
        [text set-text] (hooks/use-state {:one "" :two ""})]
    (letfn [(handle-save []
              (let [p (p/deferred)]
                 (p/future
                   (POST "/api/example"
                         {:params {:code (code-fn) :data (data-fn)}
                          :timeout 3000
                          :handler       (fn [response] (positive-response! response p))
                          :error-handler (fn [{:keys [status status-text]}] (error-response! status status-text p))}))
                (-> p
                    (p/then (fn [_]
                              (if-let [save-id (-> @response-atm :save-id)]
                                (do (js/console.log "Setting url to " save-id)
                                    (set-url save-id)
                                    (set-text {:one "To recover the work shown visit:" :two save-id}))
                                (set-text {:one "Communication with the server failed." :two  ""}))
                              (set-open true))))))
            (handle-close [] (set-open false))]
      ($ "div"
         ($ IconButton {:onClick handle-save} ($ Save {:sx white-style}))
         ($ Modal {:open open
                   :onClose handle-close
                   :aria-labelledby "save-modal-title"
                   :aria-describedby "save-modal-description"}
            ($ Box {:sx style}
               ($ Typography {:id "save-modal-title" :variant "h6" :component "h6"}
                  (:one text))
               ($ Typography {:id "save-modal-description" :sx {:mt 20}} (:two text))))))))

;;; ($read [["schema/name" "urn:oagis-10.8:Nouns:Invoice"],  ["schema-object"]])
(def test-obj
   {:ident-type "schema/name"
    :ident-val "urn:oagis-10.8.4:Nouns:Invoice"
    :request-objs "schema-object"})

(defn tryme []
  (-> (GET "/api/graph-query"
           {:params test-obj
            :handler handler
            :error-handler error-handler
            :timeout 5000})))

(defn tryme2 []
  (-> (GET "/api/health"
           {:handler handler
            :error-handler error-handler})))
