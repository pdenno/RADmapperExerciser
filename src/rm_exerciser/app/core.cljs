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

(declare init editor)

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

;;; grid-template:
;;;             "h h" 40px
;;;             "d e" 40px
;;;             "d o" 40px / 1fr 1fr;
(defn home-page [_s]
  [:body {:style {:width  "800px" #_(str (.-innerWidth js/window)  "px")
                  :height "500px" #_(str (.-innerHeight js/window) "px")}}
   [:section.hero.is-primary.is-small ; ToDo: ...but not small enough!
    [:div.hero-body [:div.title.is-large "RADmapper Exerciser" ]]]
   [:div {:class "container"
          :style {:display "grid" :grid-gap "0px"
                  :overflow "auto" ; This keep the size from following the mouse.
                  :height "500px" ; This needed or whole-window vertical follows mouse.
                  :grid-template-columns "auto" ; "1fr 1fr" (1fr means it can't get bigger")
                  :grid-template-rows "auto"
                  :margin-left  "0px" :margin-right "0px"  :margin-top "0px"  :margin-bottom "0px"
                  :padding-left "0px" :padding-right "0px" :padding-top "0px" :padding-bottom "0px"
                  :max-width "90%"}} ; This makes a difference on the left!
    [:div {:class "item"
           :style {:grid-column-start 1  :grid-column-end 2
                   :border "3px solid" :overflow "auto" :resize "horizontal" :height "100%"}}
     [:textarea {:defaultValue "/*   Use in-lined data for the time being!  */"
                 :style {:overflow "auto"
                         :width "100%" :height "100%" :resize "none"}}]] ; :resize "none" or you get another resizing controller.
    [editor init-text {:eval? true}]]])

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

(defn editor [source {:keys [eval?]}]
  (r/with-let [!view (r/atom nil)
               last-result (r/atom nil) ; POD guessing.
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
    [:div {:class "item container"
           :style {:border "3px solid"
                   :overflow "auto" ; give unwanted scroll bar, but keeps the size controlled
                   :resize "none"
                   :height "100%"
                   :max-height "100%"
                   :grid-column-start 2 :grid-column-end 3 :grid-row-start 1 :grid-item-rows "auto"
                   #_#_#_#_#_#_:width "100%" :height "100%" :min-width "10%"}} ; These don't seem to do anything
     [:div {:class "item"
            :style {:overflow "auto"
                    :resize "vertical"
                    :max-height "90%" ; This is keep the output textarea from disappearing. How?
                    :min-height "10%"
                    :border "3px solid"
                    :scroll-x "true"
                    :lineWrapping "false"
                    ;:margin "0px" #_"auto" ; This makes the editor "separate" from the output text area"
                    #_#_:max-width "90%"}}  ; This only screws things up.
      [:div {:style {:lineWrapping "false" :margin "auto"} :ref mount!}]]
     [:textarea
      {:class "textarea is-family-monospace editable monospace text-sm m-0"
       :style {:height "20%" :max-width "90vw" :max-height "90%" :min-height "10%" :resize "none" #_#_:overflow "auto"}
       :onChange #(println "I'm having fun:" %) ; ToDo: Probably could be better ;^)
       :value (or (when-some [{:keys [error result]} @last-result]
                    (.log js/console (str "result = " (or result error)))
                    (if error
                      (str error)
                      (str result)))
                  "Ctrl-Enter above to execute.")}]]
    (finally
      (println "In finally")
      (j/call @!view :destroy))))
