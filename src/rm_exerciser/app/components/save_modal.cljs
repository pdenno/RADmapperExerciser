(ns rm-exerciser.app.components.save-modal
  (:require
   [ajax.core :refer [GET POST]]
   [applied-science.js-interop :as j]
   [clojure.pprint             :refer [cl-format]]
   [helix.core :refer [defnc $]]
   [helix.hooks :as hooks]
   [promesa.core :as p]
   [rm-exerciser.app.util :refer [component-refs]]
   [rm-exerciser.app.components.editor :refer [set-editor-text get-editor-text]]
   ["@mui/material/Box$default" :as Box]
   ["@mui/material/IconButton$default" :as IconButton]
   ["@mui/icons-material/Save$default" :as Save]
   ["@mui/material/Typography$default" :as Typography]
   ["@mui/material/Modal$default" :as Modal]
   [taoensso.timbre :as log :refer-macros [info debug log]]))

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

;;; ToDo: My use of promesa seems excessively complex, but it took me a while just to get this far!
(def response-atm "An atom for communication of responses for AJAX calls." (atom nil))
(defn positive-response!
  "Set the response atm and resolve the promise"
  [response p]
  (js/console.log (str "CLJS-AJAX returns:" response))
  (reset! response-atm response)
  (p/resolve! p response))

(defn error-response!
  [status status-text p]
  (js/console.log (str "CLJS-AJAX error: status= " status " status-text= " status-text))
  (p/resolve! p))

;;; This was a bad idea, I think! (Waiting seems impossible!)
#_(defn server-ok? []
  (let [p (p/deferred)]
    (reset! diag p)
    (GET "/api/health"
         {:handler (fn [response] (positive-response! response p))
          :error-handler (fn [{:keys [status status-text]}] (error-response! status status-text p))})
    (p/bind p (fn [x] (js/console.log "Done waiting: x = " x)
                (p/resolved @response-atm)))))

;;; https://stackoverflow.com/questions/951021/what-is-the-javascript-version-of-sleep
;;; const sleep = ms => new Promise(r => setTimeout(r, ms));
;;; function sleep(ms) {
;;;    return new Promise(resolve => setTimeout(resolve, ms));
;;; new() just doesn't work for this!

;;; next try (js/Promise #(.setTimeout % ms)). What is the new?!??!
;;; BETTER THAN THAT, READ ABOUT JS/PROMISE <====================================================
;;; const sleep = ms => new Promise(r => setTimeout(r, ms));
(defn sleep [ms] (.resolve js/Promise #(.setTimeout % ms)))

(defn tryme []
  (-> (sleep 4000)
      (.then (js/console.log "********************** Done."))))


;(defn sleep [ms] (.setTimeout (.resolve js/Promise) ms))

#_(defn sleep [ms]
  (-> js/Promise
      (.resolve #(.setTimeout % 4000))
      (.then #(println "Done!" %))))

;;; const sleep = ms => new Promise(r => setTimeout(r, ms));
#_(defn sleep [ms]
  (-> js/Promise
      (.resolve #(.setTimeout % 4000))
      (.then #(println "Done!" %))))

#_(defn sleep [ms]
  (-> (js/Promise.resolve #(.setTimeout % 4000))
      (.then #(println "Done!" %))))

#_(defn sleep [ms]
  (-> (js/Promise.resolve (.setTimeout 4000))
      (.then #(println "Done!" %))))


#_(defn sleep [ms]
  (-> js/Promise (.setTimeout ms)))

#_(defn sleep [ms]
  (-> (.resolve js/Promise)
      (.setTimeout ms)))


;;; https://mui.com/material-ui/react-modal/
(defnc SaveModal [{:keys [code-fn data-fn]}]
  (let [[open, set-open] (hooks/use-state false)
        [_url set-url]   (hooks/use-state nil)
        [text set-text]  (hooks/use-state {:one "" :two ""})]
    (letfn [(handle-save []
              (let [p (p/deferred)]
                (POST "/api/example"
                      {:params {:code (code-fn) :data (data-fn)}
                       :timeout 3000
                       :handler       (fn [response] (positive-response! response p))
                       :error-handler (fn [{:keys [status status-text]}] (error-response! status status-text p))})
                (-> p
                    (p/then (fn [_]
                              (if-let [save-id (-> @response-atm :save-id)]
                                (do (js/console.log "User code saved at " save-id)
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

#_(defn tryme []
  (let [p (p/deferred)]
    (GET "/api/graph-query"
         {:params test-obj
          :handler (fn [response] (positive-response! response p))
          :error-handler (fn [{:keys [status status-text]}] (error-response! status status-text p))
          :timeout 5000})
    (p/-> (p/bind p (fn [x] (js/console.log "Done waiting: x = " x)
                      (p/resolved @response-atm)))
          (fn [_]
            (js/console.log "Setting editor text."
            (set-editor-text "data" "small update!" #_(-> @response-atm :objects str)))))))


;;; Since this is returning a promise at the REPL, I still don't understand Promesa!
#_(defn server-ok? []
  (let [p (p/deferred)]
    (p/future (GET "/api/health"
                   {:handler (fn [response] (positive-response! response p))
                    :error-handler (fn [{:keys [status status-text]}] (error-response! status status-text p))}))
    (p/chain p
             (fn [_] (= "up" (-> @response-atm :app :status)))
             (fn [x] (js/console.log "In catch x = " x) :Foo))))
