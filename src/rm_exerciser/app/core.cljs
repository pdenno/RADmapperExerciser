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

(defn home-page [_s]
  [:div
   [:section.hero.is-small.is-link ; This probably won't work because it must be "the main container".
    [:div.hero-body
     [:div.title.is-large "RADmapper Exerciser"]]]
   [:div.tile.is-ancestor
    ;; LHS
    [:div.tile.is-parent ; .is-horizontal
     [:article.tile.is-child
      [:p.title.is-6 "Source data"]
      [:textarea.textarea]
      #_[:div.button.is-success "Save"]
      #_[:a {:href "https://bulma.io/documentation/elements/title/"} "Bulma page"]]
     ;; RHS
     [:div.tile.is-parent.is-vertical
      [:article.tile.is-child.is-12
       [editor init-text {:eval? true}]]]]]])

(def app-state
  (r/atom {:rand (rand)}))

(defn ^:dev/after-load mount-root []
  (rdom/render [home-page app-state] (.getElementById js/document "app")))

(defn ^:export ^:dev/once init! [] (mount-root))

;;;------------------------------------------
;;; From clojure-mode/demo, but very similar to clojure-mode-demo.
(def theme
  (.theme EditorView
          (j/lit {".cm-content" {:white-space "pre-wrap"
                                 :padding "10px 0"
                                 :flex "1 1 0"}

                  "&.cm-focused" {:outline "0 !important"}
                  ".cm-line" {:padding "0 9px"
                              :line-height "1.6"
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
    [:div
     [:div {:class "rounded-md mb-0 text-sm monospace overflow-auto relative border shadow-lg bg-white"
            :ref mount!
            :style {:max-height 610}}]
     [:article.tile.is-child.is-12
      [:p.title.is-6 "Output"]
      [:textarea.textarea.is-success
       {:class "monospace text-sm overflow-auto"
        :value
        (when-some [{:keys [error result]} @last-result]
          (.log js/console (str "result = " (or result error)))
          (if error
            (str error)
            (str result)))}]]]
    (finally
      (println "In finally")
      (j/call @!view :destroy))))
