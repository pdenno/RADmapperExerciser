(ns rm-exerciser.app.core
  (:require
   [rm-exerciser.app.rm-mode :as rm-mode]
   [rm-exerciser.app.rm-mode.test-utils :as test-utils]
   [rm-exerciser.app.sci-eval :as sci-eval]
   [rad-mapper.evaluate :as ev]
   [rm-exerciser.app.rm-mode.extensions.eval-region :as eval-region]
   ["@codemirror/language" :refer [foldGutter syntaxHighlighting defaultHighlightStyle]]
   ["@codemirror/commands" :refer [history historyKeymap]]
   ["@codemirror/state" :refer [EditorState]]
   ["@codemirror/view" :as view :refer [EditorView]]
   [applied-science.js-interop :as j]
   ;["@mui/material/colors" :as colors]
   ["@mui/material/Box$default" :as MuiBox]
   ["@mui/material/Stack$default" :as Stack]
   ["@mui/material/styles" :as styles]
   ["@mui/material/Typography$default" :as Typography]
   ["@mui/material/TextField$default" :as TextField]
   ["@mui/material/CssBaseline" :as CssBaseline]
   [rm-exerciser.app.components.share :refer [ShareUpDown ShareLeftRight]]
   [helix.core :refer [defnc $ <>]]
   [helix.hooks :as hooks]
   [helix.dom :as d]
   ["react-dom/client" :as react-dom]
   [taoensso.timbre :as log :refer-macros [info debug log]]))

(def exerciser-theme
  (styles/createTheme
   (j/lit {#_#_:palette {:primary   colors/yellow
                         :secondary colors/green}
           :typography {:subtitle1 {:fontSize 5}}

           :components {#_#_:MuiCssBaseline {:blah "blah"}

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
(def editor-theme
  (.theme EditorView
          (j/lit {".cm-content" {:white-space "pre-wrap"
                                 :padding "10px 0"
                                 :flex "1 1 0"}
                  "&.cm-focused" {:outline "0 !important"}
                  ".cm-line" {:padding "0 9px"
                              :line-height "1.1"
                              :font-size "11px"
                              :font-family "'JetBrains Mono', monospace"}
                  ".cm-matchingBracket" {:border-bottom "1px solid var(--teal-color)"
                                         :color "inherit"}
                  ".cm-gutters" {:background "transparent"
                                 :border "none"}
                  ".cm-gutterElement" {:margin-left "5px"}
                  ;; only show cursor when focused
                  ".cm-cursor" {:visibility "hidden"}
                  "&.cm-focused .cm-cursor" {:visibility "visible"}})))

;;; ToDo: Comments were messing up parse?!?
(def init-data
"   $DBa := [{'email' : 'bob@example.com', 'aAttr' : 'Bob-A-data',   'name' : 'Bob'},
            {'email' : 'alice@alice.org', 'aAttr' : 'Alice-A-data', 'name' : 'Alice'}];

   $DBb := [{'id' : 'bob@example.com', 'bAttr' : 'Bob-B-data'},
            {'id' : 'alice@alice.org', 'bAttr' : 'Alice-B-data'}];")

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

(defonce extensions #js[editor-theme
                        (history)
                        (syntaxHighlighting defaultHighlightStyle)
                        (view/drawSelection)
                        (foldGutter)
                        (.. EditorState -allowMultipleSelections (of true)) ; https://codemirror.net/docs/ref/
                        rm-mode/default-extensions
                        (.of view/keymap rm-mode/complete-keymap)
                        (.of view/keymap historyKeymap)])

(def last-result (atom nil))
(def diag (atom {}))

;;; Unfinished
#_(defn run-thing [{:keys [modifier on-result]}]
   ;;(let [on-result ^:js {:keys [state]}]
  (log/info "sci-eval Extension")
  (.of view/keymap
       (j/lit
        [{:key "Mod-Enter"
          :run (partial eval-cell on-result)}])))

;;; I pass editor-state so as not to iterative append the init-code.
(defnc EditorStack [{:keys [user-data editor-state result]}]
  ($ ShareUpDown
     {:up ($ MuiBox
             {:ref (fn [el]
                     (when el
                       (new EditorView ; https://codemirror.net/docs/ref/
                             (j/obj :state editor-state :parent el))))})
      :down ($ TextField {:multiline true
                          :minRows 4
                          :fullWidth true
                          :placeholder "Ctrl-Enter above to execute."
                          :value (:result result)})}))

(def initialized? (atom false))

(defnc Top []
  (let [[user-data set-user-data] (hooks/use-state {:data-str init-data})
        [result set-result] (hooks/use-state {:result "Ctrl-Enter above to execute." :error false})
        editor-state (test-utils/make-state
                      (-> #js [extensions]
                          (.concat #js [(sci-eval/extension
                                         {:modifier "Alt"
                                          :on-result set-result})])) ; <========== Wrap set-result here. (at least for diagnostics).
                      (if @initialized? "" init-code))]
    (hooks/use-effect [result]
       (js/console.log "I run when result changes: result =" (:result result)))
    (reset! initialized? true)
    ($ Stack {:direction "column" :spacing 0}
       ($ Typography
          {:variant "h4" :color "white" :backgroundColor "primary.main" :padding "2px 0 2px 30px" :noWrap false}
          "RADmapper") ; <==== Nice, but not what I can do to the following with variant subtitle1.
       ($ ShareLeftRight
          {:left  ($ TextField {#_#_:variant "data-editor" ; Invalid prop `variant` of value `data-editor` supplied to `ForwardRef`,
                                :multiline   true      ; expected one of ["filled","outlined","standard"].
                                :fullWidth   true
                                :minRows     15
                                :placeholder "Use in-lined data for the time being!"
                                :onChange    (fn [& _args]
                                               (set-user-data {:data-str (str user-data " ")})) ; At least a trivial change is required
                                :width       "200px" ; Does nothing.
                                :height      "200px"
                                :value        (:data-str user-data)})

           :right ($ EditorStack
                     {:user-data user-data
                      :editor-state editor-state
                      :result result})}))))

(defnc app []
  {:helix/features {:check-invalid-hooks-usage true}}
  (<> ; https://reactjs.org/docs/react-api.html#reactfragment
   #_(d/div (editor init-text {:eval? true}))
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
