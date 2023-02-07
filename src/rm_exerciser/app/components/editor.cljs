(ns rm-exerciser.app.components.editor
  (:require
   [clojure.string :as str]
   [rm-exerciser.app.rm-mode.parser :as parser]
   [rm-exerciser.app.rm-mode.state :as state]
   ["@codemirror/language" :refer [foldGutter syntaxHighlighting defaultHighlightStyle]]
   ["@codemirror/commands" :refer [history #_historyKeymap emacsStyleKeymap]]
   ["@codemirror/view" :as view :refer [EditorView ViewPlugin ViewUpdate MeasureRequest #_lineNumbers]]
   ["@codemirror/state" :refer [EditorState Compartment ChangeSet Transaction]]
   [applied-science.js-interop :as j]
   ;["@mui/system/sizing" :as sizing] ; ToDo: Investigate
   ["@mui/material/FormControl$default" :as FormControl]
   ["@mui/material/MenuItem$default" :as MenuItem]
   ["@mui/material/Select$default" :as Select]
   [rm-exerciser.app.components.examples :as examples :refer [rm-examples get-example]]
   [rm-exerciser.app.components.share :as share :refer [resize-default]]
   [helix.core :as helix :refer [defnc $]]
   [helix.hooks :as hooks]
   [helix.dom :as d]
   [shadow.cljs.modern :refer (defclass)]
   [taoensso.timbre :as log :refer-macros [info debug log]]))

(def diag (atom nil))

(def new-height (atom nil))

(defn measure-read ; ToDo: rename these (geom-change etc.)
  "Returns the ViewState object so that measure-write can operate on it."
  [editor-view]
  (log/info "=====Reading height " (j/get-in editor-view [:viewState :editorHeight]))
  (j/get editor-view :viewState))

(defn measure-write [view-state editor-view]
  (when-let [height @new-height]
    (j/assoc! view-state :contentDOMHeight height)
    (j/assoc! view-state :editorHeight height)
    (log/info "=====Writing height =" height " editor-view =" editor-view)))

(defn modify-geom
  "Called from SWP.update() when the argument update.geometryChanged = true."
  [#^EditorView editor-view]
  (log/info "============ modify-geom ====")
  (.requestMeasure
   editor-view
   ^MeasureRequest (j/obj :read  measure-read
                          :write measure-write
                          :key "editorHeight")))

(defclass SoftWrapPlugin
  (constructor [this init-data] ; init-data is a EditorView.
               (super init-data))
  ;; adds regular method, protocols similar to deftype/defrecord also supported
  Object
  (update [this #^ViewUpdate update]
          (when (j/get update :geometryChanged) ; It WILL work! (I checked.)
            (modify-geom (j/get update :view)))))

(def #^ViewPlugin soft-wrap
  "A JS array (for extensions) that adds an .update method to EditorView for modifying geometry."
  #js [(.define ViewPlugin (fn [#^EditorView view]
                             (new SoftWrapPlugin view)))])


;;; https://codemirror.net/docs/ref/#state.ChangeSpec
;;; Because the selectors will be prefixed with a scope class, rules that directly match the editor's wrapper element -- to which
;;; the scope class will be added -- need to be explicitly differentiated by adding an & to the selector for that
;;; element, for example &.cm-focused.
(defn editor-theme
  "We need to be able to reconfigure the theme because '& {:max-height <height>}' must be the current height
   of the resizable editor. When the height of the text exceeds max-height, scroll bars appear.
   Without this adjustment the text runs outside the editor!"
  [height]
  (.theme EditorView
          (j/lit {".cm-editor"   {:resize   "both"         ; Supposedly these allow it to be resized.
                                  :height   "auto"         ; https://discuss.codemirror.net/t/editor-variable-height/3523
                                  :width    "auto"}        ; overflow "hidden" does nothing. (Was discussed on 3523.)
                  "&"            {:max-height (str height "px")} ; This brings in scroll-bars at the right time...
                  ".cm-scroller" {:overflow "auto"}              ; ... See https://codemirror.net/examples/styling/
                  ".cm-comment"  {:color "#9933CC"}
                  "&.cm-focused" {:outline "0 !important"} ; As above, this matches the editor's wrapper element.
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

;;; By wrapping part of your configuration in a compartment, you can later replace that part through a transaction.
(def style-compartment (new Compartment)) ; ToDo: defonce

;;; The editor state class is a persistent (immutable) data structure.
;;; To update a state, you create a transaction, which produces a new state instance, without modifying the original object.
;;; /I THINK he uses "create a transaction" as meaning "run a transaction"./ YES. See below.
;;; Yet .update creates a Transaction object AND state is immutable, so it seems that I need to do something with this Transaction???

;;; THIS SAYS IT ALL (you need only create the transaction)
;;; let state = EditorState.create({doc: "hello world"})
;;; let transaction = state.update({changes: {from: 6, to: 11, insert: "editor"}})
;;; console.log(transaction.state.doc.toString()) // "hello editor"
;;; I have done this very sort of thing below to switch new files in. I DID A DISPATCH VIEW AFTERWARDS.

;;; I'm going to bring back the wrapping to see whether anything is changed by tryme. DOES NOTHING!

;;; rm_exerciser.app.components.editor.style_compartment.of(...).reconfigure is not a function
;;;  * Compartment reconfigure(content: Extension) → StateEffect<unknown>
;;;         Create an effect that reconfigures this compartment.
;;;         Doing this will DISCARD ANY EXTENSIONS APPENDED, but does not reset the content of reconfigured compartments.
;;;       /That sounds like the exact opposite of what I am trying to achieve!/

(defn tryme [height]
  (let [^EditorView  view   (j/get (.getElementById js/document "data-editor") :view)
        ^EditorState state  (j/get view :state)
        ^StateEffect effect (.reconfigure #^Compartment style-compartment (editor-theme height)) ; How else do I get the change in?!?!
        ^Transaction trans  (.update state (j/lit {:effects [effect]}))
        ^ChangeSet   cset   (j/get trans :effects)]
    (reset! diag cset)
    #_(.update state #js [{:effects {:reconfigure (.of style-compartment (editor-theme height))}}])
    (.dispatch view (j/lit {:effects cset}))
    (j/get-in cset [0 :desc :sections])))

(defonce extensions #js[(.of style-compartment (editor-theme 222)) ; 300px default.
                        (history) ; This means you can undo things!
                        (syntaxHighlighting defaultHighlightStyle)
                        (view/drawSelection)
                        (foldGutter)
                        (.. EditorState -allowMultipleSelections (of true))
                        ;;parser/default-extensions ; Related to fold gutter, at least. Causes 2023-02-06 "Comma bug"!
                        (.of view/keymap parser/complete-keymap)
                        (.of view/keymap emacsStyleKeymap #_historyKeymap)])

(defn set-editor-text [editor-name text]
  (let [^EditorView  view  (-> js/document (.getElementById editor-name) (j/get :view))
        ^EditorState state (j/get view :state)
        ^ChangeSpec  change (j/lit {:from 0 :to (j/get-in state [:doc :length]) :insert text})]
    (.dispatch view (j/lit {:changes change}))))

#_(defn set-editor-text [editor-name text]
  (let [^EditorView  view  (-> js/document (.getElementById editor-name) (j/get :view))
        start (atom 0)]
    (doseq [line (clojure.string/split text #"\r?\n" -1)]
      (let [stop (+ @start (count line) 1)
            ^ChangeSpec change (j/lit {:from @start :insert (str line "\n")})]
        (.dispatch view (j/lit {:changes change}))
        (swap! start #(+ % stop))))))

(defnc SelectExample
  [{:keys [init-example]}]
  (let [[example set-example] (hooks/use-state init-example)]
    ($ FormControl {:size "small" ; small makes a tiny difference
                    :sx {:height "25%"
                         :maxHeight "25%"}}
       ($ Select {:variant "filled"
                  :sx {:style {:height "20px"}}
                  :value example
                  :onChange (fn [_e v]
                              (let [ex-name (j/get-in v [:props :value])
                                    example (get-example ex-name)]
                                (set-example ex-name)
                                (set-editor-text "code-editor" (:code example))
                                (set-editor-text "data-editor" (:data example))))}
          (for [ex rm-examples]
            ($ MenuItem {:key (:name ex) :value (:name ex)} (:name ex)))))))

(defn resize-editor
  "Set dimension of the EditorView for share."
  [parent width height]
  (resize-default parent width height)
  (let [view (-> parent (j/get :children) (.item 0) (j/get :view))
        dom  (j/get view [:dom])]
    (reset! diag {:view view})
    ;;(log/info "Editor component is " (-> parent (j/get :children) (.item 0) (j/get :id)))
    (when width  (j/assoc-in! dom [:style :width]  (str width  "px")))
    (when height (j/assoc-in! parent [:style :max-height] (str height "px")))
    (when height (j/assoc-in! dom [:style :max-height] (str height "px")))))

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
    (d/div {:ref ed-ref :id name #_#_:style {:border-style "solid"}} ; style works but looks ugly because it wraps editor tightly.
           @view-dom))) ; id used for retrieval.
