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
   [rm-exerciser.app.util :as util]
   [rm-exerciser.app.components.examples :as examples :refer [rm-examples get-example]]
   [rm-exerciser.app.components.share :as share]
   [helix.core :as helix :refer [defnc $]]
   [helix.hooks :as hooks]
   [helix.dom :as d]
   ["react" :as react]
   [shadow.cljs.modern :refer (defclass)]
   [taoensso.timbre :as log :refer-macros [info debug log]]))

(def diag (atom nil))

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
(defonce style-compartment (new Compartment))

(defonce extensions #js[(.of style-compartment (editor-theme 422)) ; 300px default.
                        (history) ; This means you can undo things!
                        (syntaxHighlighting defaultHighlightStyle)
                        (view/drawSelection)
                        (foldGutter)
                        (.. EditorState -allowMultipleSelections (of true))
                        ;;parser/default-extensions ; Related to fold gutter, at least. Causes 2023-02-06 "Comma bug"!
                        (.of view/keymap parser/complete-keymap)
                        (.of view/keymap emacsStyleKeymap #_historyKeymap)])


(defn set-editor-text [editor-name text]
  (let [^EditorView  view (-> js/document (.getElementById editor-name) (j/get :view))
        ^EditorState state (j/get view :state)
        ^ChangeSpec  change (j/lit {:from 0 :to (j/get-in state [:doc :length]) :insert text})]
    (.dispatch view (j/lit {:changes change}))))

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

(declare resize-finish-x)

;;; ToDo: Check whether or not this is even needed!
(defn resize
  "Set dimension of the EditorView for share."
  [parent width height]
  (share/resize parent width height)
  (when-let [view (-> parent (j/get :children) (.item 0) (j/get :view))]
    (let [dom (j/get view [:dom])]
      ;;(reset! diag {:view view})
      ;;(log/info "Editor component is " (-> parent (j/get :children) (.item 0) (j/get :id)))
      (when width  (j/assoc-in! dom [:style :width]  (str width  "px")))
      (when height
        (j/assoc-in! parent [:style :height] (str height "px")) ; ToDo: I dropped :max-height here. OK?
        (resize-finish-x view height)))))

(defn resize-finish-x
  [^EditorView view height]
  (log/info "==========resize-finish-x: height = " height " view = " view)
    (let [^EditorState state  (j/get view :state)
          ^StateEffect effect (.reconfigure #^Compartment style-compartment (editor-theme height))
          ^Transaction trans  (.update state (j/lit {:effects [effect]}))]
    (.dispatch view trans)))

(defn resize-finish
  "In order for scroll bars to appear (and long text not to run past the end of the editor vieport),
   :max-height must be set. This is done with a transaction."
  [names elem height]
  (reset! diag {:elem elem})
  (when-let [ view (j/get-in elem [:children 0 :children 0 :children 0 :view])]
    (log/info "==========resize-finish: names = " names  " elem = " elem " height = " height " view = " view)
    (let [^EditorState state  (j/get view :state)
          ^StateEffect effect (.reconfigure #^Compartment style-compartment (editor-theme height))
          ^Transaction trans  (.update state (j/lit {:effects [effect]}))]
    (.dispatch view trans))))

;;; ToDo: For the time being, I'm assuming every editor gets its own copy of the extensions.
;;;       To check this, get the values
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
           (swap! util/component-refs #(assoc % name parent))
           (j/assoc-in! ed-ref [:current :view] view)))) ; Nice! ToDo: But is it a legit approach?
    (log/info "====== editor named " name)
    (reset! diag
     (d/div {:ref ed-ref :id name} ; style works but looks ugly because it wraps editor tightly.
            @view-dom)))) ; id used for retrieval.
