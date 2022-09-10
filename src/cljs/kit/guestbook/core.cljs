(ns kit.guestbook.core
  (:require
   #_[reagent.core :as r]                                                                 ; clojure-mode.demo and kit
   [reagent.dom :as rdom]                                                               ; clojure-mode.demo and kit
   #_["react" :as react]
   #_["@codemirror/highlight" :as highlight]                                             ; clojure-mode-demo (2yrs old).
   #_["@codemirror/history" :refer [history historyKeymap]]                              ; clojure-mode-demo (2yrs old).
   [nextjournal.clojure-mode :as cm-clj]                                                 ; both
   [nextjournal.clojure-mode.live-grammar :as live-grammar]                              ; clojure-mode.demo  
   ["@codemirror/language" :refer [foldGutter syntaxHighlighting defaultHighlightStyle]] ; clojure-mode.demo
   ["@codemirror/commands" :refer [history historyKeymap]]                               ; clojure-mode.demo
   ["@codemirror/state" :refer [EditorState]]                                            ; both
   ["@codemirror/view" :as view :refer [EditorView]]))                                   ; both

;;; This file is a combination of things from
;;;   1) ~/Documents/git/clojure/clojure-mode/demo/src/nextjournal/clojure_mode/demo.cljs   (called clojure-mode.demo here)
;;;   2) ~/Documents/git/clojure/clojure-mode-demo/src/cm/demo.cljs                         (called clojure-mode-demo here)
;;; ToDo: Study clojure-mode.demo more, if only because it has special provisions for linux and mac.


;;; I borrowed the initialization stuff from ~/Documents/git/clojure/guestbook/modules/kit-modules/cljs/assets/src/
;;; ToDo: Maybe its't time to try something like this with nothing but http-kit!

;;; From Kit
(defn home-page []
  [:div [:h2 "Welcome to Reagent!"]])

;;; -------------------------
;;; Initialize app

;;; From Kit
(defn ^:dev/after-load mount-root []
  (rdom/render [home-page] (.getElementById js/document "app")))

;;; From Kit
(defn ^:export ^:dev/once init! []
  (mount-root))

;;;------------------------------------------
;;; From clojure-mode
(def theme
  (.theme EditorView
          (clj->js {:$content {:white-space "pre-wrap"
                               :padding "10px 0"}
                    :$$focused {:outline "none"}
                    :$line {:padding "0 9px"
                            :line-height "1.6"
                            :font-size "16px"
                            :font-family "var(--code-font)"}
                    :$matchingBracket {:border-bottom "1px solid var(--teal-color)"
                                       :color "inherit"}
                    :$gutters {:background "transparent"
                               :border "none"}
                    :$gutterElement {:margin-left "5px"}
                    ;; only show cursor when focused
                    :$cursor {:visibility "hidden"}
                    "$$focused $cursor" {:visibility "visible"}})))

;;; clojure-mode-demo
#_(defonce extensions
  #js[theme
      (history)
      highlight/defaultHighlightStyle
      (view/drawSelection)
      (.. EditorState -allowMultipleSelections (of true))
      cm-clj/default-extensions
      (.of view/keymap cm-clj/complete-keymap)
      (.of view/keymap historyKeymap)])

(defonce extensions #js[theme
                        (history)
                        (syntaxHighlighting defaultHighlightStyle)
                        (view/drawSelection)
                        (foldGutter)
                        (.. EditorState -allowMultipleSelections (of true))
                        (if false
                          ;; use live-reloading grammar
                          #js[(cm-clj/syntax live-grammar/parser)
                              (.slice cm-clj/default-extensions 1)]
                          cm-clj/default-extensions)
                        (.of view/keymap cm-clj/complete-keymap)
                        (.of view/keymap historyKeymap)])


;;; From Kit
#_(defn init []
  (println "Hello World"))

;;; From clojure-mode-demo
(defn init []
  (EditorView. #js {:state (.create EditorState #js {:doc "(def answer\n  (+ (* 4 10) 2))"
                                                     :extensions extensions})
                    :parent (.getElementById js/document "app")})) ; POD was "demo"
