(ns rm-exerciser.app.core
  (:require
   [reagent.core :as reagent]
   [reagent.dom :as rdom]
   ["react" :as react]
   [rm-exerciser.app.rm-mode :as rm-mode]
   [rm-exerciser.app.rm-mode.test-utils :as test-utils]
   [rm-exerciser.app.sci-eval :as sci-eval]
   ["@codemirror/language" :refer [foldGutter syntaxHighlighting defaultHighlightStyle]]
   ["@codemirror/commands" :refer [history historyKeymap]]
   ["@codemirror/state" :refer [EditorState]]
   ["@codemirror/view" :as view :refer [EditorView]]
   [applied-science.js-interop :as j]
   ;; ------------ stuff from helix experiment ----------------------------------
   ["@mui/material/Typography$default" :as Typography]
   ["@mui/material/TextField$default" :as TextField]
   ["@mui/material/Stack$default" :as Stack]
   ["@mui/material/styles" :as styles]
   ;["@mui/material/CssBaseline" :as CssBaseline]
   ["@mui/material/colors" :as colors]
   [rm-exerciser.app.components.share :refer [ShareUpDown ShareLeftRight]]
   [helix.core :refer [defnc $ <>]]
   [helix.dom :as d]
   ["react-dom/client" :as react-dom]))

(declare editor last-result)

;;; ToDo: Comments were messing up parse?!?
(def init-text
"(  $DBa := [{'email' : 'bob@example.com', 'aAttr' : 'Bob-A-data',   'name' : 'Bob'},
             {'email' : 'alice@alice.org', 'aAttr' : 'Alice-A-data', 'name' : 'Alice'}];

   $DBb := [{'id' : 'bob@example.com', 'bAttr' : 'Bob-B-data'},
            {'id' : 'alice@alice.org', 'bAttr' : 'Alice-B-data'}];

   $qFn :=  query(){[$DBa ?e1 :email ?id]
                    [$DBb ?e2 :id    ?id]
                    [$DBa ?e1 :name  ?name]
                    [$DBa ?e1 :aAttr ?aData]
                    [$DBb ?e2 :bAttr ?bData]};

   $bSet := $qFn($DBa, $DBb);

   $eFn := express(){{?id : {'name'  : ?name,
                             'aData' : ?aData,
                             'bData' : ?bData}}};

   $reduce($bSet, $eFn) )")

(def exerciser-theme
  (styles/createTheme
   (j/lit {#_#_:palette {:primary   colors/yellow
                         :secondary colors/green}
           :components {:MuiDivider
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

(defn home-page [_s]
  [:div
   {:style {:margin-left  "0px" :margin-right "0px"  :margin-top "0px"  :margin-bottom "0px" ; Margins/padding useless everywhere!
            :padding-left "0px" :padding-right "0px" :padding-top "0px" :padding-bottom "0px"
            :border "3px solid" #_"3px dotted red"}}
   [:section.hero.is-primary.is-small ; ToDo: ...but not small enough!
    [:div.hero-body [:div.title.is-large "RADmapper Exerciser" ]]]
   [:container ; Warning: The tag <container> is unrecognized in this browser.
    {:style {:display "grid"
             :grid-template-areas "\"data  editor\" \n \"data output\""
             :grid-template-columns "auto-fit auto-fit" ; Does nothing
             :grid-template-row     "auto-fit auto-fit"
             :grid-column-gap "0px"
             :grid-row-gap "0px"
             :grid-gutter-width  "0px" ; Does nothing.
             :grid-gutter-height "0px" ; Does nothing.
             :overflow "hidden" ; This needed or whole-window horizontal follows the mouse.
             :height "100%"     ; This needed or whole-window vertical   follows the mouse. <============ STILL
             :border "3px solid"}}
    [:div.item
     {:style {:grid-area "data"
              :flex "1 1 auto"
              :display "flex"
              :resize "horizontal"
              :justify-content "stretch"
              :border "3px solid"
              :overflow "auto"}}
     [:textarea
      {:defaultValue "/* FACTORED  Use in-lined data for the time being!  */"
       :onChange #(println "resizing textarea!:" %)
       :style {:flex "1 1 auto"
               :object-fit "cover"
               :border "3px solid"
               :overflow "auto"
               :padding-left "5px"
               :padding-top "5px"
               :resize "auto"}}]]
    [editor init-text {:eval? true}]
    [:div.item
     [:textarea
        :onChange #(println "I'm having fun:" %) ; ToDo: Probably could be better ;^)
        :value (or (when-some [{:keys [error result]} @last-result]
                     (.log js/console (str "result = " (or result error)))
                     (if error
                       (str error)
                       (str result)))
                   "Ctrl-Enter above to execute.")]]]])

;;;------------------------------------------
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

(defonce extensions #js[editor-theme
                        (history)
                        (syntaxHighlighting defaultHighlightStyle)
                        (view/drawSelection)
                        (foldGutter)
                        (.. EditorState -allowMultipleSelections (of true))
                        rm-mode/default-extensions
                        (.of view/keymap rm-mode/complete-keymap)
                        (.of view/keymap historyKeymap)])

(def last-result (reagent/atom nil))

(defn editor [source {:keys [eval?]}]
  (reagent/with-let
    [!view (reagent/atom nil)
     mount! (fn [el]
              (when el
                (reset! !view (new EditorView
                                   (j/obj :state
                                          (test-utils/make-state
                                           (cond-> #js [extensions]
                                             eval? (.concat #js [(sci-eval/extension
                                                                  {:modifier "Alt"
                                                                   :on-result (partial reset! last-result)})]))
                                           source)
                                          :parent el)))))]
    (d/div {:ref mount! :style {:display "flex" :object-fit "cover" :lineWrapping "false" :margin "auto"}})
    #_(finally
        (println "In finally")
        (j/call @!view :destroy))))

;;;------------------------------------------------------------------------------
(defnc form []
  ($ Stack {:direction "column"
            :spacing   0}
     ($ Typography {:variant         "h4"
                    :color           "white"
                    :backgroundColor "primary.main"
                    :padding         "2px 0 2px 30px"
                    :noWrap  false}
        "RADmapper")
     ($ ShareLeftRight
        {:heigth "100%"
         :left  ($ TextField {#_#_:variant "data-editor"
                              :multiline true
                              :fullWidth true
                              :placeholder "Use in-lined data for the time being!"
                              :width  "200px" ; Does nothing.
                              :height "200px"})
         :right ($ ShareUpDown
                   {:up   (d/div (editor init-text {:eval? true}))
                    :down ($ TextField {:multiline true
                                        :fullWidth true
                                        :value (or (when-some [{:keys [error result]} @last-result]
                                                     (.log js/console (str "result = " (or result error)))
                                                     (if error
                                                       (str error)
                                                       (str result)))
                                                   "Ctrl-Enter above to execute.")})})})))

(defnc app []
  {:helix/features {:check-invalid-hooks-usage true}}
  (<> ; https://reactjs.org/docs/react-api.html#reactfragment
   #_(d/div (editor init-text {:eval? true}))
   ;(CssBaseline) ; ToDo: Investigate purpose of CssBaseline.
   ($ styles/ThemeProvider
      {:theme exerciser-theme}
      ($ form))))

(def app-state  (reagent/atom {:rand (rand)}))

(defonce root (react-dom/createRoot (js/document.getElementById "app")))

(defn ^{:after-load true, :dev/after-load true} mount-root []
  (.render root ($ app)))

(defn ^:export init []
  (mount-root))
