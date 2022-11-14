(ns rm-exerciser.app.core
  (:require
   [reagent.core :as r]
   [reagent.dom :as rdom]
   ["react" :as react]
   [rm-exerciser.app.rm-mode :as rm-mode]
   [rm-exerciser.app.rm-mode.test-utils :as test-utils]
   [rm-exerciser.app.sci-eval :as sci-eval]
   ["@codemirror/language" :refer [foldGutter syntaxHighlighting defaultHighlightStyle]]
   ["@codemirror/commands" :refer [history historyKeymap]]
   ["@codemirror/state" :refer [EditorState]]
   ["@codemirror/view" :as view :refer [EditorView]]
   [applied-science.js-interop :as j]))

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

;;; ToDo: Some kind of :onResize (of the window)
(defn home-page [_s]
  [:body
   {:style {:width  "auto" #_"100%" #_(str (- (.-innerWidth  js/window)  15) "px") ; https://ishadeed.com/article/auto-css/
            :height "auto" #_"100%" #_(str (- (.-innerHeight js/window) 100) "px")
            :margin-left  "0px" :margin-right "0px"  :margin-top "0px"  :margin-bottom "0px" ; Margins/padding useless everywhere!
            :padding-left "0px" :padding-right "0px" :padding-top "0px" :padding-bottom "0px"
            :border "3px dotted red"}}
   [:section.hero.is-primary.is-small ; ToDo: ...but not small enough!
    [:div.hero-body [:div.title.is-large "RADmapper Exerciser" ]]]
   [:container
    {:componentDidMount #(println "mounting!:" %)
     :onMount #(println "rmmmesizing container!:" %)
     :onChange #(println "resizing container!:" %)
     :style {:display "grid"
             :grid-template-areas "\"data  editor\" \n \"data output\""
             :grid-template-columns "auto-fit auto-fit" ; Does nothing
             :grid-template-row     "auto-fit auto-fit"
             ;;:grid-template-columns "repeat(auto-fit, minmax(200px,1fr) )" ; Cause overrun (as though the LHS isn't ther)
             ;;:grid-template-row     "repeat(auto-fit, minmax(200px,1fr) )"
             :grid-column-gap "0px"
             :grid-row-gap "0px"
             :grid-gutter-width  "0px" ; Does nothing.
             :grid-gutter-height "0px" ; Does nothing.
             :overflow "hidden" ; This needed or whole-window horizontal follows the mouse.
             :height "100%"     ; This needed or whole-window vertical   follows the mouse. <============ STILL
             :border "3px solid"}}
    [:item
     {:on-change #(println "resizing item!:" %)
      :style {:grid-area "data"
              :flex "1 1 auto"
              :display "flex"
              :resize "horizontal"
              :justify-content "stretch"
              :border "3px solid"
              :overflow "auto"}}
     [:textarea
      {:defaultValue "/* FACTORED  Use in-lined data for the time being!  */"
       :onChange #(println "resizing textarea!:" %)
       :style {:width "auto" #_"100%"
               :height "100%"
               :flex "1 1 auto"
               :object-fit "cover"
               :border "3px solid"
               :overflow "auto"
               :padding-left "5px"
               :padding-top "5px"
               :resize "auto"}}]]
    [editor init-text {:eval? true}]
    [:item
     {:style {:grid-area "output"
              :display "flex"
              :flex "1 1 auto"
              :justify-content "stretch"
              :object-fit "cover"
              :border "3px dotted green" :overflow "hidden" :resize "none"}}
     [:textarea
      {:style {:display "flex"
               :height "100%"
               :width "100%"
               :background-color "#EFF0EB" ; ToDo: Add through sass.
               :overflow "auto"
               :padding-left "5px"
               :padding-top  "5px"
               :resize "none"}
        :onChange #(println "I'm having fun:" %) ; ToDo: Probably could be better ;^)
        :value (or (when-some [{:keys [error result]} @last-result]
                     (.log js/console (str "result = " (or result error)))
                     (if error
                       (str error)
                       (str result)))
                   "Ctrl-Enter above to execute.")}]]]])

(def app-state
  (r/atom {:rand (rand)}))

(defn ^:dev/after-load mount-root []
  (rdom/render [home-page app-state] (.getElementById js/document "app")))

(defn ^:export ^:dev/once init! [] (mount-root))

;;;------------------------------------------
;;; https://codemirror.net/examples/styling/
(def theme
  (.theme EditorView
          (j/lit {".cm-content" {:white-space "pre-wrap"
                                 :padding "10px 0"
                                 :flex "1 1 0"}
                  "&.cm-focused" {:outline "0 !important"}
                  ".cm-line" {:padding "0 9px"
                              :line-height "1.3"
                              :font-size "15px"
                              :font-family "'JetBrains Mono', monospace"}
                  ".cm-matchingBracket" {:border-bottom "1px solid var(--teal-color)"
                                         :color "inherit"}
                  ".cm-gutters" {:background "transparent"
                                 :border "none"}
                  ".cm-gutterElement" {:margin-left "5px"}
                  ;; only show cursor when focused
                  ".cm-cursor" {:visibility "hidden"}
                  "&.cm-focused .cm-cursor" {:visibility "visible"}})))

(defonce extensions #js[theme
                        (history)
                        (syntaxHighlighting defaultHighlightStyle)
                        (view/drawSelection)
                        (foldGutter)
                        (.. EditorState -allowMultipleSelections (of true))
                        rm-mode/default-extensions
                        (.of view/keymap rm-mode/complete-keymap)
                        (.of view/keymap historyKeymap)])

(def last-result (r/atom nil))

(defn editor [source {:keys [eval?]}]
  (r/with-let [!view (r/atom nil)
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
    [:item
     {:style {:grid-area "editor"
              :display "flex"
              :flex "1 1 auto" ; Or "1 1 0"  https://ishadeed.com/article/auto-css/
              :width "100%"
              :height "100%"
              :flex-basis "auto"
              :justify-content "stretch"
              :resize "vertical"
              :overflow "auto"
              :object-fit "cover"
              :border "3px solid"
              :scroll-x "true" }}
     [:div {:ref mount! :style {:display "flex" :object-fit "cover" :lineWrapping "false" :margin "auto"}}]]
      (finally
        (println "In finally")
        (j/call @!view :destroy))))
