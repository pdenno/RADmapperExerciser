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
   ["@mui/material/InputLabel$default" :as InputLabel]
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
(defn get-user-data ;<===================================================================== Fix this soon and clean up here! (use a ref)
  "Return the string content of the data editor."
  []
  ;; https://stackoverflow.com/questions/10285301/how-to-get-the-value-of-codemirror-textarea
  ;; See also the very helpful https://codemirror.net/docs/migration/ (section "Getting the Document and Selection)
  (log/info "======== get-user-data: atom =" @data-editor-atm)
  (if-let [s (j/get-in @data-editor-atm [:state :doc])]
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

(defnc ResultTextField
  [{:keys [result]}]
  ;; See https://mui.com/material-ui/react-text-field/  useFormControl
  ;; Also exerciser/src/rm_exerciser/app/core.cljs
  ;; Especially,    :InputProps  {:end-adornment (r/as-element [input-adornment {:position "end"} "Baz"])}}]
  ($ TextField {:id "result-field"
                #_#_:variant "standard" #_"dataEditor"
                #_#_:style {:width "100px" :height "100px"}
                :multiline true
                :minRows 4
                :fullWidth true
                :placeholder "Ctrl-Enter above to execute."
                :value result}))

#_(defnc ResultTextField
  [{:keys [result value] :or {value (or result "Ctrl-Enter above to execute.")}}]
  ($ "textarea" {:style {:width 200 :height 200}
                 :cols 80
                 :row  10
                 :value value}))

(defn resize-editor
  "Set dimension of the EditorView for share."
  [parent width height]
  (resize-default parent width height)
  (let [dom (-> parent (j/get :children) (.item 0) (j/get-in [:view :dom]))]
    ;(log/info "Editor component is " (-> parent (j/get :children) (.item 0) (j/get :id)))
    (when width  (j/assoc-in! dom [:style :width]  (str width  "px")))
    (when height (j/assoc-in! dom [:style :height] (str height "px")))))

(defnc Editor
  [{:keys [text ext-adds atm name] :or {ext-adds #js []}}]
  (let [ed-ref (hooks/use-ref nil)
        editor-state (state/make-state (-> #js [extensions] (.concat ext-adds)) text)
        view-dom (atom nil)]
    (hooks/use-effect []
       (when-let [parent (j/get ed-ref :current)]
         (let [view (new EditorView (j/obj :state editor-state :parent parent))]
           (reset! view-dom (j/get view :dom))
           (when atm (reset! atm view))
           (j/assoc-in! ed-ref [:current :view] view)))) ; Nice!
    (d/div {:ref ed-ref :id name} @view-dom))) ; :id for debugging.

;;;  I think I want transactions against the state of each editor in the onChange
;;;  (-> js/document (.getElementById "code-editor") (j/get-in [:view :state]))
;;; let state = EditorState.create({doc: "hello world"})
;;; let transaction = state.update({changes: {from: 6, to: 11, insert: "editor"}})
;;; console.log(transaction.state.doc.toString()) // "hello editor"
;;; 'changes' is a ChangeSpec:
;;; type ChangeSpec = {from: number, to⁠?: number, insert⁠?: string | Text} |
;;; ChangeSet |
;;; readonly ChangeSpec[]
;;;
;;;    This type is used as argument to EditorState.changes and in the changes field of transaction specs to succinctly
;;;    describe document changes.
;;;    It may either be a plain object describing a change (a deletion, insertion, or replacement, depending on which fields are present),
;;;    a change set, or an array of change specs.
;;;
;;; https://discuss.codemirror.net/t/codemirror-6-setting-the-contents-of-the-editor/2473/2
;;; You could create a transaction like state.update({changes: {from: 0, to: state.doc.length, insert: "foobar"}})
;;; to replace the entire document.
(defn update-text-for-example [editor-name text] :nyi)

(defnc SelectExample
  [{:keys [init-example] :or {init-example "2 Databases"}}]
  (let [[example set-example] (hooks/use-state init-example)]
    (hooks/use-effect [example]
      ;; This gets called on initialization, so might be some repetition.
      (log/info "Changed example to " example ". Do something!"))
    ($ FormControl {:size "small"}
       ($ Select {:variant "filled"
                  :value example
                  :onChange (fn [_e v]
                              (let [ex-name (j/get-in v [:props :value])]
                                (set-example ex-name)
                                (update-text-for-example "code-editor" (-> ex-name get-example :code))
                                (update-text-for-example "data-editor" (-> ex-name get-example :data))))}
          (for [ex rm-examples]
            ($ MenuItem {:key (:name ex) :value (:name ex)} (:name ex)))))))

;;; ToDo: Find a more react-idiomatic approach than data-editor-atm. (hooks/use-ref maybe?)
(defnc Top [{:keys [top-width rm-example]}]
  (let [[result set-result] (hooks/use-state {:success "Ctrl-Enter above to execute."})]
    ($ Stack {:direction "column" #_#_:spacing 0}
       ($ Typography
          {:variant "h4" :color "white" :backgroundColor "primary.main" :padding "2px 0 2px 30px" :noWrap false}
          "RADmapper")
       ($ ShareLeftRight
          {:left
           ($ Stack {:direction "column" :spacing "10px"}
              ($ SelectExample {:init-example (:name rm-example)})
              ($ Editor  {:text (:data rm-example)
                          :name "data-editor"
                          :atm data-editor-atm}))
           :right
           ($ ShareUpDown
              {:init-height 400 ; ToDo: Fix this (and next)
               :up ($ Editor {:text (:code rm-example)
                              :ext-adds #js [(add-result-action {:on-result set-result})]
                              :name "code-editor"})
               :on-resize-up resize-editor
               :dn ($ ResultTextField {:result (if-let [success (:success result)] success (:failure result))})})
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
