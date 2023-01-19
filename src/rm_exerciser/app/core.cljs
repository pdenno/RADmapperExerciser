(ns rm-exerciser.app.core
  (:require
   [clojure.string :as str]
   [rm-exerciser.app.rm-mode.parser :as parser]
   [rm-exerciser.app.rm-mode.state :as state]
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
   ["@mui/material/CssBaseline" :as CssBaseline]
   ;["@mui/material/InputAdornment$default" :as InputAdornment]
   ["@mui/material/Stack$default" :as Stack]
   ["@mui/material/styles" :as styles]
   ["@mui/material/Typography$default" :as Typography]
   ["@mui/material/TextField$default" :as TextField]
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
          (j/lit {".cm-content" {:white-space "pre-wrap"
                                 :padding "5px 0"
                                 :height "fit-content !important" ; guessing does nothing
                                 :maxLines 3 ; guessing does nothing.
                                 :flex "1 1 0"}
                  ;".cm-linenumber" {:color "#969896"} ; guessing from FarhadG
                  "&.cm-focused" {:outline "0 !important"}
                  ".cm-line" {:padding "0 9px"
                              :line-height "1.1"
                              ; :color "red" WORKS!
                              :font-size "11px"
                              :font-family "'JetBrains Mono', monospace"}
                  ".cm-matchingBracket" {:border-bottom "1px solid var(--teal-color)"
                                         :color "inherit"}
                  ".cm-gutters" {:background "lightgray" ;"transparent"
                                 ;:box-shadow "1px 0 2px 0 rgba(0, 0, 0, 0.5)" ; works
                                 :lineNumbers true ; guessing does nothing
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
                        parser/default-extensions
                        (.of view/keymap parser/complete-keymap)
                        (.of view/keymap historyKeymap)])

(def user-data-atm "Set as a hook effect for use in RM evaluation." (atom nil))
(def data-editor-atm "Set to the data editor object" (atom nil)) ; ToDo: Find a more react idiomatic way to do this.

;;; Problem: This is using the atom data-editor-atm, which isn't reliable for some reason.
(defn get-user-data
  "Return the string content of the data editor."
  []
  ;; https://stackoverflow.com/questions/10285301/how-to-get-the-value-of-codemirror-textarea
  (log/info "======== get-user-data: atom =" @data-editor-atm)
  (.toString (j/get (j/get @data-editor-atm .state) .doc)))

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

(defn add-on-result-action
  "Return the keymap updated with the partial for :on-result, I think!" ;<===
  [{:keys [on-result]}]
  (log/info "sci-eval Extension")
  (.of view/keymap
       (j/lit
        [{:key "Mod-Enter"
          :run (partial eval-cell on-result)}])))

;;; ToDo: Find a more react-idiomatic approach than the two atoms initialized? and data-editor-atm. (hooks/use-ref maybe?)
(def initialized? "Use to suppress adding init-{data/code} to editors" (atom false))

(defnc Top []
  (let [[result set-result] (hooks/use-state {:success "Ctrl-Enter above to execute."}) ; These things don't have to be objects!
        code-editor-state (state/make-state
                           (-> #js [extensions] (.concat #js [(add-on-result-action {:on-result set-result})]))
                           (if @initialized? "" init-code))
        data-editor-state (state/make-state
                           #js [extensions]
                            (if @initialized? (get-user-data) init-data))] ; Problem here is that it adds it again! (get extra copies).
    (reset! initialized? true)
    ($ Stack {:direction "column" :spacing 0}
       ($ Typography
          {:variant "h4" :color "white" :backgroundColor "primary.main" :padding "2px 0 2px 30px" :noWrap false}
          "RADmapper")
       ($ ShareLeftRight
          {:left ($ MuiBox {:ref (fn [el] ; <======================= Use this (somehow hooks/use-ref :current?) closure?
                                   (log/info "=====Calling the data editor. el =" el)
                                   (when el
                                     (reset! data-editor-atm
                                             (new EditorView ; https://codemirror.net/docs/ref/
                                                  (j/obj :state data-editor-state :parent el)))))})
           :right ($ ShareUpDown
                     {:up ($ MuiBox {:ref (fn [el]
                                            (when el
                                              (new EditorView ; https://codemirror.net/docs/ref/
                                                   (j/obj :state code-editor-state :parent el))))})
                      ;; See https://mui.com/material-ui/react-text-field/  useFormControl
                      ;; Also exerciser/src/rm_exerciser/app/core.cljs
                      ;; Especially,    :InputProps  {:end-adornment (r/as-element [input-adornment {:position "end"} "Baz"])}}]
                      :down ($ TextField {:sx {:color "text.secondary"}
                                          :multiline true
                                          :minRows 4
                                          ;:inputProps {:endAdornment ($ InputAdornment {:position "end"} "Baz")}
                                          :fullWidth true
                                          :placeholder "Ctrl-Enter above to execute."
                                          :value (if-let [success (:success result)]
                                                   success
                                                   (:failure result))})})}))))

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
