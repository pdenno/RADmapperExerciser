(ns rm-exerciser.app.core
  (:require
   [clojure.string :as str]
   [rm-exerciser.app.rm-mode.parser :as parser]
   [rm-exerciser.app.rm-mode.state :as state]
   [rad-mapper.evaluate :as ev]
   ["@codemirror/language" :refer [foldGutter syntaxHighlighting defaultHighlightStyle]]
   ["@codemirror/commands" :refer [history #_historyKeymap emacsStyleKeymap]]
   ["@codemirror/view" :as view :refer [EditorView #_lineNumbers]]
   ["@codemirror/state" :refer [EditorState]]
   [applied-science.js-interop :as j]
   ;["@mui/material/colors" :as colors]
   ["@mui/material/CssBaseline" :as CssBaseline]
   ;;["@mui/material/InputAdornment$default" :as InputAdornment]
   ["@mui/material/FormControl$default" :as FormControl]
   ["@mui/material/MenuItem$default" :as MenuItem]
   ["@mui/material/Select$default" :as Select]
   ["@mui/material/Stack$default" :as Stack]
   ["@mui/material/styles" :as styles]
   ["@mui/material/Typography$default" :as Typography]
   ["@mui/material/TextField$default" :as TextField]
   [rm-exerciser.app.examples :as examples :refer [rm-examples get-example]]
   [rm-exerciser.app.components.share :as share :refer [ShareUpDown ShareLeftRight resize-default]]
   [helix.core :as helix :refer [defnc $ <>]]
   [helix.hooks :as hooks]
   [helix.dom :as d]
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

(def editor-theme
  (.theme EditorView
          (j/lit {".cm-editor"   {:resize   "both"         ; Supposedly these allow it to be resized (when used with requestMeasure).
                                  :height   "auto"         ; https://discuss.codemirror.net/t/editor-variable-height/3523
                                  :width    "auto"}        ; overflow "hidden" does nothing. (Was discussed on 3523.)
                  ".cm-comment"  {:color "#9933CC"}
                  "&.cm-focused" {:outline "0 !important"}
                  ".cm-line"     {:padding "0 9px"
                                  :line-height "1.1"
                                  :font-size "11px"
                                  :font-family "'JetBrains Mono', monospace"}
                  ".cm-matchingBracket" {:border-bottom "1px solid var(--teal-color)"
                                         :color "inherit"}
                  ".cm-gutters"         {:background "lightgray" ;"transparent"
                                         :overflow "auto" ; scroll bars appear as needed.
                                         :border "none"}
                  ".cm-gutterElement"   {:margin-left "5px"}
                  ".cm-cursor"          {:visibility "hidden"} ; only show cursor when focused
                  "&.cm-focused .cm-cursor" {:visibility "visible"}})))

(defonce extensions #js[editor-theme
                        (history) ; This means you can undo things!
                        (syntaxHighlighting defaultHighlightStyle)
                        (view/drawSelection)
                        (foldGutter)
                        (.. EditorState -allowMultipleSelections (of true))
                        ;;parser/default-extensions ; Related to fold gutter, at least. Causes 2023-02-06 "Comma bug"!
                        (.of view/keymap parser/complete-keymap)
                        (.of view/keymap emacsStyleKeymap #_historyKeymap)])

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

(defn resize-editor
  "Set dimension of the EditorView for share."
  [parent width height]
  (resize-default parent width height)
  (let [dom (-> parent (j/get :children) (.item 0) (j/get-in [:view :dom]))]
    ;(log/info "Editor component is " (-> parent (j/get :children) (.item 0) (j/get :id)))
    (when width  (j/assoc-in! dom [:style :width]  (str width  "px")))
    (when height (j/assoc-in! dom [:style :height] (str height "px")))))

(defnc Editor
  [{:keys [text ext-adds atm name] :or {ext-adds #js []}}] ; atm for debugging.
  (let [ed-ref (hooks/use-ref nil)
        txt (if (string? text) text (or (:success text) (:failure text) ""))
        editor-state (state/make-state (-> #js [extensions] (.concat ext-adds)) txt)
        view-dom (atom nil)]
    (hooks/use-effect []
       (when-let [parent (j/get ed-ref :current)]
         (let [view (new EditorView (j/obj :state editor-state :parent parent))]
           (reset! view-dom (j/get view :dom))
           (when atm (reset! atm view))
           (j/assoc-in! ed-ref [:current :view] view)))) ; Nice! ToDo: But is it a legit approach?
    (d/div {:ref ed-ref :id name} @view-dom))) ; id used for retrieval.

(defn set-editor-text [editor-name text]
  (let [^EditorView  view  (-> js/document (.getElementById editor-name) (j/get :view))
        ^EditorState state (j/get view :state)
        ^ChangeSpec  change (j/lit {:from 0 :to (j/get-in state [:doc :length]) :insert text})]
    (.dispatch view (j/lit {:changes change}))))

(defnc SelectExample
  [{:keys [init-example]}]
  (let [[example set-example] (hooks/use-state init-example)]
    ($ FormControl {:size "small"}
       ($ Select {:variant "filled"
                  :value example
                  :onChange (fn [_e v]
                              (let [ex-name (j/get-in v [:props :value])
                                    example (get-example ex-name)]
                                (set-example ex-name)
                                (set-editor-text "code-editor" (:code example))
                                (set-editor-text "data-editor" (:data example))))}
          (for [ex rm-examples]
            ($ MenuItem {:key (:name ex) :value (:name ex)} (:name ex)))))))

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
