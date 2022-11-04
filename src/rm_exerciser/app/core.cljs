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
"(  $DBa := [{'id' : 123, 'aAttr' : 'Bob-A-data',   'name' : 'Bob'},
            {'id' : 234, 'aAttr' : 'Alice-A-data', 'name' : 'Alice'}];

   $DBb := [{'id' : 123, 'bAttr' : 'Bob-B-data'},
            {'id' : 234, 'bAttr' : 'Alice-B-data'}];

   $qFn :=  query(){[$DBa ?e1 :id    ?id]
                    [$DBb ?e2 :id    ?id]
                    [$DBa ?e1 :name  ?name]
                    [$DBa ?e1 :aAttr ?aData]
                    [$DBb ?e2 :bAttr ?bData]};

   $bSets := $qFn($DBa, $DBb);

   $eFn := express(){{?id : {'name'  : ?name,
                             'aData' : ?aData,
                             'bData' : ?bData}}};

   $reduce($bSets, $eFn) )")

;;; ToDo: Some kind of :onResize (of the window)
(defn home-page [_s]
  [:body
   {:style {:width  #_"800px" (str (- (.-innerWidth js/window) 15)  "px")
            :height #_"500px" (str (.-innerHeight js/window) "px")
            :margin-left  "0px" :margin-right "0px"  :margin-top "0px"  :margin-bottom "0px" ; Margins/padding useless everywhere!
            :padding-left "0px" :padding-right "0px" :padding-top "0px" :padding-bottom "0px"}}
   [:section.hero.is-primary.is-small ; ToDo: ...but not small enough!
    [:div.hero-body [:div.title.is-large "RADmapper Exerciser" ]]]
   [:container
    {:style {:display "grid" :grid-gap "0px" :grid-gutter-width "0px"
             :overflow "hidden" ; This needed or whole-window horizontal follows the mouse.
             #_#_:grid-template-columns "auto"
             #_#_:grid-template-rows "auto"
             :border "3px solid"
             :margin-left  "0px" :margin-right "0px"  :margin-top "0px"  :margin-bottom "0px"
             :padding-left "0px" :padding-right "0px" :padding-top "0px" :padding-bottom "0px"
             #_#_:max-width "90%"}} ; No longer makes a difference. (and doesn't work)
    [:textarea
      {:defaultValue "/*   Use in-lined data for the time being!  */"
       :style {:gird-row-start    1 :gird-row-end    3
               :grid-column-start 1 :grid-column-end 2
               :max-width "60%" ; Would like to do 90/10 here (with shrinks at 90%. Why?
               :min-width "10%"
               :border "3px solid"
               :overflow "auto"
               :resize "horizontal"
               :padding-left "5px"
               :padding-top "5px"
               #_:width "100%"
               #_:height "100%"}}]
    [editor init-text {:eval? true}]
    [:textarea
       {:class "textarea is-family-monospace editable monospace text-sm m-0"
        :style {:grid-row-start    2 :grid-row-end    3 
                :grid-column-start 2 :grid-column-end 3
                :border "3px solid"
                :height "100%"
                :background-color "#EFF0EB" ; ToDo: Add through sass.
                :overflow "auto"
                ;;:max-width "100%" ; Does nothing
                :max-height "90%" ; These match ...
                :min-height "10%" ; the thing it is sharing with ................................WORKING!
                :padding-left "5px"
                :padding-top  "5px"
                :resize "none"}
        :onChange #(println "I'm having fun:" %) ; ToDo: Probably could be better ;^)
        :value (or (when-some [{:keys [error result]} @last-result]
                     (.log js/console (str "result = " (or result error)))
                     (if error
                       (str error)
                       (str result)))
                   "Ctrl-Enter above to execute.")}]]])

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
                                 ;; :flex makes it a flex box and no scroll horizontal scroll bar.
                                 #_#_ :flex "1 1 0"}
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
     {:ref mount!
      :style {:grid-row-start    1 :grid-row-end    2
              :grid-column-start 2 :grid-column-end 3
              :overflow "auto"
              :resize "vertical"
              :max-height "90%" ; This is keep the output textarea from disappearing...
              :min-height "10%" ; and match the thing it is sharing with.................. WORKING!
              :max-width "100%"
              :margin-left  "0px" :margin-right "0px"  :margin-top "0px"  :margin-bottom "0px"
              :padding-left "0px" :padding-right "0px" :padding-top "0px" :padding-bottom "0px"
              :border "3px solid"
              :scroll-x "true"
              :lineWrapping "false"}}]
      (finally
        (println "In finally")
        (j/call @!view :destroy))))
