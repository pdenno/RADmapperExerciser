(ns rm-exerciser.app.core
  (:require
   [goog.object :as gobj]
   [clojure.string :as str]
   [rm-exerciser.app.rm-mode.parser :as parser]
   [rm-exerciser.app.rm-mode.state :as state]
   [rad-mapper.evaluate :as ev]
   ["@codemirror/language" :refer [foldGutter syntaxHighlighting defaultHighlightStyle]]
   ["@codemirror/commands" :refer [history #_historyKeymap emacsStyleKeymap]]
   ["@codemirror/state" :refer [EditorState]]
   ["@codemirror/view" :as view :refer [EditorView ViewPlugin ViewUpdate MeasureRequest #_lineNumbers]]
   [applied-science.js-interop :as j]
   ;["@mui/material/colors" :as colors]
   ["@mui/material/CssBaseline" :as CssBaseline]
   ;;["@mui/material/InputAdornment$default" :as InputAdornment]
   ["@mui/material/Stack$default" :as Stack]
   ["@mui/material/styles" :as styles]
   ["@mui/material/Typography$default" :as Typography]
   ["@mui/material/TextField$default" :as TextField]
   [rm-exerciser.app.components.share :as share :refer [ShareUpDown ShareLeftRight resize-default]]
   [helix.core :as helix :refer [defnc $ <>]]
   [helix.hooks :as hooks]
   [helix.dom :as d]
   ["react-dom/client" :as react-dom]
   [shadow.cljs.modern :refer (defclass)]
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
                        {:variants [{:props {:variant "active-vert" } ; vertical divider of horizontal layout
                                     :style {:cursor "ew-resize"
                                             :color "black"
                                             :width 5}}
                                    {:props {:variant "active-horiz" } ; vertical divider of horizontal layout
                                     :style {:cursor "ns-resize"
                                             :color "black"
                                             :height 5}}]}
                        :MuiTextField
                        {:variants [{:props {:variant "data-editor"}
                                     #_#_:style {:multiline true
                                                 :width 200}}]}}})))

;;; https://codemirror.net/examples/styling/
;;; https://github.com/FarhadG/code-mirror-themes/tree/master/themes
(def editor-theme
  (.theme EditorView
          (j/lit {".cm-editor" {:resize   "both"         ; Supposedly these allow it to be resized (when used with requestMeasure).
                                :height   "auto"         ; https://discuss.codemirror.net/t/editor-variable-height/3523
                                :overflow "hidden"}
                  ".cm-content" {:white-space "pre-wrap"
                                 :padding "5px 0"        ; Allows wrapping
                                 :height "auto"
                                 :flex "1 1 0"}
                  ".cm-wrap"     {:border "1px solid silver"}
                  ".cm-comment" {:color "#9933CC"}
                  "&.cm-focused" {:outline "0 !important"}
                  ".cm-line" {:padding "0 9px"
                              :line-height "1.1"
                              :font-size "11px"
                              :font-family "'JetBrains Mono', monospace"}
                  ".cm-matchingBracket" {:border-bottom "1px solid var(--teal-color)"
                                         :color "inherit"}
                  ".cm-gutters" {:background "lightgray" ;"transparent"
                                 :border "none"}
                  ".cm-gutter, .cm-content" {:overflow "auto"}  ; scroll bars appear as needed.
                  ".cm-gutterElement" {:margin-left "5px"}
                  ".cm-cursor" {:visibility "hidden"} ; only show cursor when focused
                  "&.cm-focused .cm-cursor" {:visibility "visible"}})))

(def init-data
"   $DBa := [{'email' : 'bob@example.com', 'aAttr' : 'Bob-A-data',   'name' : 'Bob'},
            {'email' : 'alice@alice.org', 'aAttr' : 'Alice-A-data', 'name' : 'Alice'}];

   $DBb := [{'id' : 'bob@example.com', 'bAttr' : 'Bob-B-data'},
            {'id' : 'alice@alice.org', 'bAttr' : 'Alice-B-data'}];")
#_(def init-data "***small data***")

(def init-code
   "( $qFn :=  query(){[$DBa ?e1 :email ?id]
                   [$DBb ?e2 :id    ?id]
                   [$DBa ?e1 :name  ?name]
                   [$DBa ?e1 :aAttr ?aData]
                   [$DBb ?e2 :bAttr ?bData]};

   $bSet := $qFn($DBa, $DBb);

   $eFn := express(){{?id : {'name'  : ?name,
                             'aData' : ?aData,
                             'bData' : ?bData}}};

   $reduce($bSet, $eFn) )")

;;;(def init-code "***small code****")

;;; ToDo: Find a more react idiomatic way to do these.
(def new-height "For communication between resize-editor and measure-write" (atom nil))
(def data-editor-atm "Set to an EditorView object" (atom nil))
#_(def code-editor-atm "Set to an EditorView object" (atom nil))
(defonce extensions #js[editor-theme
                        ;;soft-wrap
                        ;;(lineNumbers) works, but ugly.
                        ;;(.. EditorState editorHeight (of 300)) ; WIP I'm guessing.
                        (history) ; This means you can undo things!
                        (syntaxHighlighting defaultHighlightStyle)
                        (view/drawSelection)
                        (foldGutter)
                        (.. EditorState -allowMultipleSelections (of true))
                        parser/default-extensions ; related to fold gutter, at least
                        (.of view/keymap parser/complete-keymap)
                        (.of view/keymap emacsStyleKeymap #_historyKeymap)])

;;; Problem: This is using the atom data-editor-atm, which isn't reliable for some reason.
;;; The following is from https://codemirror.net/docs/migration/
;;; Similar task to that below:
;;;  Doc operations
;;;    cm.state.sliceDoc(a, b)
;;;    cm.state.doc.line(n + 1).text
;;;    cm.state.doc.lines         (This one is line count.)
;;; Selection operations
;;;    cm.state.selection.main.head   (get cursor)
;;;    cm.state.sliceDoc(cm.state.selection.main.from, cm.state.selection.main.to)
;;;    cm.state.selection.ranges.map(r => cm.state.sliceDoc(r.from, r.to))
(defn get-user-data
  "Return the string content of the data editor."
  []
  ;; https://stackoverflow.com/questions/10285301/how-to-get-the-value-of-codemirror-textarea
  ;; See also the very helpful https://codemirror.net/docs/migration/ (section "Getting the Document and Selection)
  (log/info "======== get-user-data: atom =" @data-editor-atm)
  (.toString (j/get-in @data-editor-atm [:state :doc])))

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

(defnc ResultTextField
  [{:keys [result]}]
  ;; See https://mui.com/material-ui/react-text-field/  useFormControl
  ;; Also exerciser/src/rm_exerciser/app/core.cljs
  ;; Especially,    :InputProps  {:end-adornment (r/as-element [input-adornment {:position "end"} "Baz"])}}]
  ($ TextField {:sx {:color "text.secondary"}
                :multiline true
                :minRows 4
                :fullWidth true
                :placeholder "Ctrl-Enter above to execute."
                :value result}))

(defn resize-editor
  "Set dimension of the EditorView for share."
  [parent width height]
  (resize-default parent width height)
  (let [dom (-> parent (j/get :children) (.item 0) (j/get-in [:view :dom]))]
    ;(log/info "Editor component is " (-> parent (j/get :children) (.item 0) (j/get :id)))
    (when width  (j/assoc-in! dom [:style :width]  (str width  "px")))
    (when height (j/assoc-in! dom [:style :height] (str height "px")))))

(defnc Editor
  [{:keys [editor-state init-height atm name]}]
  (let [ed-ref (hooks/use-ref nil)
        view-dom (atom nil)]
    (hooks/use-effect []
       (when-let [parent (j/get ed-ref :current)]
         (let [view (new EditorView (j/obj :state editor-state :parent parent))]
           (j/assoc-in! view [:dom :style :height] (str init-height "px"))
           (reset! view-dom (j/get view :dom))
           (when atm (reset! atm view))
           (j/assoc-in! ed-ref [:current :view] view)))) ; Nice!
    (d/div {:ref ed-ref :id name :width "100%"} @view-dom))) ; :width not helping!

;;; ToDo: Find a more react-idiomatic approach than the two atoms initialized? and data-editor-atm. (hooks/use-ref maybe?)
(def initialized? "Use to suppress adding init-{data/code} to editors" (atom false))

(defnc Top []
  (let [[result set-result] (hooks/use-state {:success "Ctrl-Enter above to execute."}) ; These things don't have to be objects!
        code-editor-state   (state/make-state
                             (-> #js [extensions] (.concat #js [(add-result-action {:on-result set-result})]))
                             (if @initialized? "" init-code))
        data-editor-state   (state/make-state
                             #js [extensions]
                             (if @initialized? (get-user-data) init-data))]
    (reset! initialized? true)
    ($ Stack {:direction "column" :spacing 0}
       ($ Typography
          {:variant "h4" :color "white" :backgroundColor "primary.main" :padding "2px 0 2px 30px" :noWrap false}
          "RADmapper")
       ($ ShareLeftRight
          {:left  ($ Editor  {:editor-state data-editor-state :name "data-editor" #_#_:atm data-editor-atm})
           :right ($ ShareUpDown
                     {:init-height 600 ; ToDo: Fix this (and next)
                      :up ($ Editor {:editor-state code-editor-state :name "code-editor" #_#_:atm code-editor-atm :init-height 130})
                      :on-resize-up resize-editor
                      :dn ($ ResultTextField {:result (if-let [success (:success result)] success (:failure result))})})}))))

(defnc app []
  {:helix/features {:check-invalid-hooks-usage true}}
  (<> ; https://reactjs.org/docs/react-api.html#reactfragment
   ;; https://mui.com/material-ui/react-css-baseline/
   ;;(CssBaseline) ; ToDo: See for example https://mui.com/material-ui/customization/typography/ search for MuiCssBaseline
   ($ styles/ThemeProvider
      {:theme exerciser-theme}
      ($ Top))))

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
