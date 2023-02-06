(ns rm-exerciser.app.core
  (:require
   [clojure.string :as str]
   [rad-mapper.evaluate :as ev]
   [applied-science.js-interop :as j]
   ["@mui/material/CssBaseline" :as CssBaseline]
   ["@mui/material/Stack$default" :as Stack]
   ["@mui/material/styles" :as styles]
   ["@mui/material/Typography$default" :as Typography]
   ["@codemirror/view" :as view]
   [rm-exerciser.app.components.editor :as editor :refer [Editor resize-editor set-editor-text SelectExample]]
   [rm-exerciser.app.components.examples :as examples :refer [rm-examples]]
   [rm-exerciser.app.components.share :as share :refer [ShareUpDown ShareLeftRight]]
   [helix.core :as helix :refer [defnc $ <>]]
   [helix.hooks :as hooks]
   ["react-dom/client" :as react-dom]
   [taoensso.timbre :as log :refer-macros [info debug log]]))

(def diag (atom {}))

;;; ToDo: Get rid of shape.borderRadius. See https://mui.com/material-ui/customization/default-theme/?expand-path=$.typography
(def exerciser-theme
  (styles/createTheme
   (j/lit {:palette {:background {:paper "#fff"}
                     #_#_:primary   colors/yellow
                     #_#_:secondary colors/green}
           :typography {:subtitle1 {:fontSize 5}}

           #_#_:text {:primary "#173A5E"
                  :secondary "#46505A"}

           :components {#_#_:MuiCssBaseline {:blah "blah"}
                        #_#_:text {:primary "#173A5E"
                                   :secondary "#46505A"}
                        :MuiDivider
                        {:variants [{:props {:variant "activeVert" } ; vertical divider of horizontal layout
                                     :style {:cursor "ew-resize"
                                             :color "black"
                                             :width 5}}
                                    {:props {:variant "activeHoriz" } ; horizontal divider of vertical layout
                                     :style {:cursor "ns-resize"
                                             :color "black"
                                             :height 4}}]}
                        :MuiTextField
                        {:variants [{:props {:variant "dataEditor"}
                                     :style {:multiline true}}]}}})))


(defn get-user-data
  "Return the string content of the data editor."
  []
  (if-let [s (-> js/document (.getElementById "data-editor") (j/get-in [:view :state :doc]))]
    (.toString s)
    ""))

(defn run-code
  "ev/processRM the source, returning a string that is either the result of processing
   or the error string that processing produced."
  [source]
  (when-some [code (not-empty (str/trim source))]
    (let [user-data (get-user-data)]
      (log/info "******* For RM eval: CODE = \n" code)
      (log/info "******* For RM eval: DATA = \n" user-data)
      (let [result (try (as-> (ev/processRM :ptag/exp code  {:execute? true :user-data user-data}) ?r
                          (str ?r)
                          {:success ?r})
                        (catch js/Error e {:failure (str "Error: " (.-message e))}))]
        (log/info "Returned from evaluation: result = " result)
        result))))

(j/defn eval-cell
  "Run RADmapper on the string retrieved from the editor's state.
   Apply the result to the argument function on-result, which was is the set-result function set up by hooks/use-state."
  [on-result ^:js {:keys [state]}]
  (-> (.-doc state)
      str
      run-code
      on-result) ;; on-result is the set-result function from hooks/use-state.
  true)          ;; This is run for its side-effect.

(defn add-result-action
  "Return the keymap updated with the partial for :on-result, I think!" ;<===
  [{:keys [on-result]}]
  (log/info "sci-eval Extension")
  (.of view/keymap
       (j/lit
        [{:key "Mod-Enter"
          :run (partial eval-cell on-result)}])))

(defnc Top [{:keys [top-width rm-example]}]
  (let [[result set-result] (hooks/use-state {:success "Ctrl-Enter above to execute."})]
    (hooks/use-effect [result] (set-editor-text "result" (or (:success result) (:failure result) "failure!")))
    ($ Stack {:direction "column" #_#_:spacing 0}
       ($ Typography
          {:variant "h4" :color "white" :backgroundColor "primary.main" :padding "2px 0 2px 30px" :noWrap false}
          "RADmapper")
       ($ ShareLeftRight
          {:left
           ($ Stack {:direction "column" :spacing "10px"}
              ($ SelectExample {:init-example (:name rm-example)})
              ($ Editor  {:text (:data rm-example) :name "data-editor"}))
           :right
           ($ ShareUpDown
              {:init-height 400 ; ToDo: Fix this.
               :up ($ Editor {:text (:code rm-example)
                              :ext-adds #js [(add-result-action {:on-result set-result})]
                              :name "code-editor"})
               :on-resize-up resize-editor
               :dn ($ Editor {:name "result" :text result})})
           :lf-pct 0.60
           :init-width top-width}))))

(defnc app []
  {:helix/features {:check-invalid-hooks-usage true}}
  (<> ; https://reactjs.org/docs/react-api.html#reactfragment
   ;; https://mui.com/material-ui/react-css-baseline/
   ;;(CssBaseline) ; ToDo: See for example https://mui.com/material-ui/customization/typography/ search for MuiCssBaseline
   ($ styles/ThemeProvider
      {:theme exerciser-theme}
      ($ Top {:top-width (- (j/get js/window :innerWidth) 10)
              :rm-example (get rm-examples 0)}))))

(defonce root (react-dom/createRoot (js/document.getElementById "app")))

(defn ^{:after-load true, :dev/after-load true} mount-root []
  (.render root ($ app)))

(defn ^:export init []
  (mount-root))

(defn get-style [dom]
  (reduce (fn [m k]
            (let [v (j/get dom (keyword k))]
              (if-let [val (and (not= v "") v)]
                (assoc m (keyword k) val)
                m)))
          {}
          (->> (-> dom (j/get :style) js-keys js->clj) (filter string?))))
