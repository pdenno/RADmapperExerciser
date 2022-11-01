(ns rm-exerciser.app.core
  (:require
   #_[rad-mapper.evaluate :as ev]
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

   $eFn := express(){{?id : {'name' : ?name, 'aData' : ?aData, 'bData' : ?bData}}};

   $reduce($bSets, $eFn) )")

;;; grid-template:
;;;             "h h" 40px
;;;             "d e" 40px
;;;             "d o" 40px / 1fr 1fr;
(defn home-page [_s]
  [:main ;{:style {:width "100%" :height "100%"}}
   [:section.hero.is-small.is-primary
    [:div.hero-body
     [:div.title.is-large "RADmapper Exerciser"]]]
   [:div {:class "container"
          :style {:display "grid" :padding "0px" :grid-gap "0px" :margin "0px"
                  :grid-template-columns "1fr auto"
                  :grid-template-rows "max-content"
                  }}
    [:div {:class "item"
           :style {:grid-column-start 1
                   :grid-column-end 2
                   :border "3px solid" :overflow "auto" :resize "horizontal"
                   :width "100%" :height "100%"}}
     [:textarea {:defaultValue "/*   Use in-lined data for the time being!  */"
                 :style {:width "100%" :height "100%"}}]]
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
                              :line-height "1.6" ;<====================
                              :font-size "16px"
                              :font-family "var(--code-font)"}
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
           :style {:border "3px solid" :overflow "auto" ; :resize "horizontal"
                   :grid-column-start 2
                   :grid-column-end 3
                   :grid-row-start 1
                   :max-width "90vw" :min-width "10vw" :height "100%" :padding "0px" :grid-gap "0px" :margin "0px"
                   :display "grid"
                   :grid-item-rows "1fr 1fr"}}

     [:div {:class "item text-sm monospace relative"
            :style {:overflow "auto" :resize "vertical" :border "3px solid" :scroll-x "true" :lineWrapping "false" :max-width "90vw"}
            :ref mount!}]
     [:textarea
      {:class "item textarea editable monospace text-sm overflow-auto m-0"
       :style {:max-width "90vw" :overflow "auto"}
       :onChange true ; ToDo: really?
       :value (or (when-some [{:keys [error result]} @last-result]
                    (.log js/console (str "result = " (or result error)))
                    (if error
                      (str error)
                      (str result)))
                  "Ctrl-Enter above to execute.")}]]
    (finally
      (println "In finally")
      (j/call @!view :destroy))))
