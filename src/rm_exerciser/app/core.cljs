(ns rm-exerciser.app.core
  (:require
   [clojure.string :as str]
   [rad-mapper.evaluate :as ev]
   [applied-science.js-interop :as j]
   ["@codemirror/view" :as view]
   ["@mui/material/colors" :as colors]
   ["@mui/material/CssBaseline$default" :as CssBaseline] ; It is a component.
   ["@mui/material/Stack$default" :as Stack]
   ["@mui/material/styles" :as styles]
   ["@mui/material/Typography$default" :as Typography]
   [rm-exerciser.app.components.editor :as editor :refer [Editor set-editor-text SelectExample]]
   [rm-exerciser.app.components.examples :as examples :refer [rm-examples]]
   [rm-exerciser.app.components.share :as share :refer [ShareUpDown ShareLeftRight]]
   [rm-exerciser.app.util :as util]
   [helix.core :as helix :refer [defnc $ <>]]
   [helix.hooks :as hooks]
   ["react" :as react]
   ["react-dom/client" :as react-dom]
   [taoensso.timbre :as log :refer-macros [info debug log]]))

(def diag (atom {}))

(def exerciser-theme
  (styles/createTheme
   (j/lit {#_#_:palette {:background {:paper "#F0F0F0"}
                     :primary   colors/yellow
                     :secondary colors/green}
           :typography {:subtitle1 {:fontSize 5}}

           #_#_:text {:primary "#173A5E"
                  :secondary "#46505A"}

           :components {:MuiCssBaseline {:text {:primary "#173A5E"
                                                :secondary "#46505A"}}
                        :MuiDivider
                        {:variants [{:props {:variant "activeVert"}  ; vertical divider of horizontal layout
                                     :style {:cursor "ew-resize"
                                             :color "black" ; Invisible on firefox.
                                             :width 4}}
                                    {:props {:variant "activeHoriz"} ; horizontal divider of vertical layout
                                     :style {:cursor "ns-resize"
                                             :color "black"  ; Invisible on firefox.
                                             :height 3}}]} ; For some reason looks better with 3, not 4.
                        :MuiTextField
                        {:variants [{:props {:variant "dataEditor"}
                                     :style {:multiline true}}]}}})))


(defn get-user-data
  "Return the string content of the data editor."
  []
  (if-let [s (get-in @util/component-refs ["data-editor" :view :state :doc])]
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

(defn get-props [obj]
  (when (map? (js->clj obj))
    (js->clj (or (j/get obj :props) (get obj "props")))))

(defn search-props
  "Return the object that has a prop that passes the test."
  [obj test]
  (let [found? (atom nil)
        cnt (atom 0)]
    (letfn [(sp [obj]
              (swap! cnt inc)
              (cond @found?                 @found?,
                    (> @cnt 500)            nil,    ; ToDo: Remove this.
                    (test obj)              (reset! found? obj),
                    (get-props obj)         (doseq [p (-> obj get-props vals)]
                                              (sp p)),
                    (vector? (js->clj obj)) (doseq [p (js->clj obj)] (sp p))))]
      (sp obj)
      @found?)))

(def top-share-fns
  "These, for convenience, keep track of what methods need be called on resizing."
  {:left-share   {:on-stop-drag-lf (partial editor/resize-finish "data-editor")}
   :right-share  {:on-resize-up    (partial editor/resize "code-editor")
                  :on-resize-dn    (partial editor/resize "result")
                  :on-stop-drag-up (partial editor/resize-finish "code-editor")
                  :on-stop-drag-dn (partial editor/resize-finish "result")}})

(defnc Top [{:keys [rm-example]}]
  (let [[result set-result] (hooks/use-state {:success "Ctrl-Enter above to execute."})
        top-width (j/get js/window :innerWidth)
        banner-height 42
        useful-height (- (j/get js/window :innerHeight) banner-height)]
    (hooks/use-effect [result] (set-editor-text "result" (or (:success result) (:failure result) "failure!")))
    (reset! util/root
    ($ Stack {:direction "column" #_#_:spacing 0 :height useful-height}
       ($ Typography
          {:variant "h4"
           :color "white"
           :backgroundColor "primary.main"
           :padding "2px 0 2px 20px"
           :noWrap false
           :height banner-height}
          "RADmapper")
       ($ ShareLeftRight
          {:left  ($ Stack {:direction "column" :spacing "10px"}
                     ($ SelectExample {:init-example (:name rm-example)})
                     ($ Editor {:text (:data rm-example) :name "data-editor"}))
           :right ($ ShareUpDown
                     {:init-height (- useful-height 20) ; ToDo: Not sure why the 20 is needed.
                      :up ($ Editor {:text (:code rm-example)
                                     :ext-adds #js [(add-result-action {:on-result set-result})]
                                     :name "code-editor"})
                      :dn ($ Editor {:name "result" :text result})
                      :share-fns (:right-share top-share-fns)})
           :share-fns (:left-share top-share-fns)
           :lf-pct 0.60
           :init-width top-width})))))

(defnc app []
  {:helix/features {:check-invalid-hooks-usage true}}
  (<> ; https://reactjs.org/docs/react-api.html#reactfragment
   ;; https://mui.com/material-ui/react-css-baseline/
   ;; ToDo: See for example https://mui.com/material-ui/customization/typography/ search for MuiCssBaseline
   ;; Use of CssBaseline removes padding/margin around application, if nothing else.
   (CssBaseline {:children #js []}) ; https://v4.mui.com/components/css-baseline/
   ($ styles/ThemeProvider
      {:theme exerciser-theme}
      ($ Top {:rm-example (get rm-examples 0)}))))

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
