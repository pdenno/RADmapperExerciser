(ns rm-exerciser.app.core
  (:require
   [clojure.string :as str]
   [rm-exerciser.app.rm-mode :as rm-mode]
   [rm-exerciser.app.rm-mode.test-utils :as test-utils]
   #_[rm-exerciser.app.sci-eval :as sci-eval]
   [rad-mapper.evaluate :as ev]
   #_[rm-exerciser.app.rm-mode.extensions.eval-region :as eval-region]
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

(def diag (atom {}))

;;; ToDo: Get rid of shape.borderRadius. See https://mui.com/material-ui/customization/default-theme/?expand-path=$.typography
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

(def init-data
"   $DBa := [{'email' : 'bob@example.com', 'aAttr' : 'Bob-A-data',   'name' : 'Bob'},
            {'email' : 'alice@alice.org', 'aAttr' : 'Alice-A-data', 'name' : 'Alice'}];

   $DBb := [{'id' : 'bob@example.com', 'bAttr' : 'Bob-B-data'},
            {'id' : 'alice@alice.org', 'bAttr' : 'Alice-B-data'}];")
#_(def init-data "***small data ***")

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

;;; ~/Documents/git/clojure/clojure-mode/demo/src/nextjournal/clojure_mode/demo.cljs
(defonce extensions #js[editor-theme
                        (history)
                        (syntaxHighlighting defaultHighlightStyle)
                        (view/drawSelection)
                        (foldGutter)
                        (.. EditorState -allowMultipleSelections (of true)) ; https://codemirror.net/docs/ref/
                        rm-mode/default-extensions
                        (.of view/keymap rm-mode/complete-keymap)
                        (.of view/keymap historyKeymap)])

(def user-data-atm "Set as a hook effect for use in RM evaluation." (atom nil))
(def data-editor-atm "Set to the data editor object" (atom nil)) ; ToDo: Find a more react idiomatic way to do this.
;;; https://stackoverflow.com/questions/10285301/how-to-get-the-value-of-codemirror-textarea
(defn get-user-data [] (.toString (j/get (j/get @data-editor-atm .state) .doc)))

(defn run-code
  "ev/processRM the source, returning a map containing :result or :error."
  [source]
  (when-some [code (not-empty (str/trim source))]
    (log/info "eval-string: code = " code)
    (let [result (try {:result (ev/processRM
                                :ptag/exp
                                code
                                {:execute? true :sci? true
                                 :user-data (get-user-data)})}
                      (catch js/Error e {:result (str "Error: " (.-message e)) :is-error? true}))]
      (log/info "eval-string: result = " result)
      result)))

(j/defn eval-cell
  "Note that this is run for side-effect on-result."
  [on-result ^:js {:keys [state]}]
  (-> (.-doc state)
      (str)
      (run-code)
      (on-result))
  true)

(defn extension [{:keys [on-result]}]
  (log/info "sci-eval Extension")
  (.of view/keymap
       (j/lit
        [{:key "Mod-Enter"
          :run (partial eval-cell on-result)}])))

(def initialized? "Use to suppress adding init-{data/code} to editors" (atom false)) ; ToDo: Find a more react-idiomatic approach???

(defnc Top []
  (let [[result set-result] (hooks/use-state {:result "Ctrl-Enter above to execute." :error false})
        code-editor-state (test-utils/make-state
                           (-> #js [extensions] (.concat #js [(extension {:on-result set-result})]))
                           (if @initialized? "" init-code))
        data-editor-state (test-utils/make-state
                           #js [extensions]
                           (if @initialized? "" init-data))]
    (reset! initialized? true)
    ($ Stack {:direction "column" :spacing 0}
       ($ Typography
          {:variant "h4" :color "white" :backgroundColor "primary.main" :padding "2px 0 2px 30px" :noWrap false}
          "RADmapper")
       ($ ShareLeftRight
          {:left ($ MuiBox {:ref (fn [el]
                                   (when el
                                     (reset! data-editor-atm
                                             (new EditorView ; https://codemirror.net/docs/ref/
                                                  (j/obj :state data-editor-state :parent el)))))})
           :right ($ ShareUpDown
                     {:up ($ MuiBox {:ref (fn [el]
                                            (when el
                                              (new EditorView ; https://codemirror.net/docs/ref/
                                                   (j/obj :state code-editor-state :parent el))))})
                      :down ($ TextField {:multiline true  ; :borderRadius 0
                                          :minRows 4
                                          :fullWidth true
                                          :placeholder "Ctrl-Enter above to execute."
                                          :value (:result result)})})}))))

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
