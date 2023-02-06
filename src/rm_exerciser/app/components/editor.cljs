(ns rm-exerciser.app.components.editor
  (:require
   [rm-exerciser.app.rm-mode.parser :as parser]
   [rm-exerciser.app.rm-mode.state :as state]
   ["@codemirror/language" :refer [foldGutter syntaxHighlighting defaultHighlightStyle]]
   ["@codemirror/commands" :refer [history #_historyKeymap emacsStyleKeymap]]
   ["@codemirror/view" :as view :refer [EditorView #_lineNumbers]]
   ["@codemirror/state" :refer [EditorState]]
   [applied-science.js-interop :as j]
   ["@mui/material/FormControl$default" :as FormControl]
   ["@mui/material/MenuItem$default" :as MenuItem]
   ["@mui/material/Select$default" :as Select]
   [rm-exerciser.app.components.examples :as examples :refer [rm-examples get-example]]
   [rm-exerciser.app.components.share :as share :refer [resize-default]]
   [helix.core :as helix :refer [defnc $]]
   [helix.hooks :as hooks]
   [helix.dom :as d]
   #_[taoensso.timbre :as log :refer-macros [info debug log]]))

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
