(ns rm-exerciser.app.util
  (:require
   [applied-science.js-interop :as j]
   ["@codemirror/view" :as view :refer [EditorView]]
   [taoensso.timbre :as log :refer-macros [info debug log]]))

(def diag (atom nil))

;;; ToDo: Is there a react way? It looks like react doesn't have this notion.
(def root "The application's root 'Symbol(react.element)' element" (atom nil))

(def component-refs
  "Some components instances are named and their refs stored here."
  (atom  {}))

;;; ToDo: Need to make this about the kind of component!
#_(defn resize-finish-method
  "If the correct information can be found in the argument element,
   run its resize-finish method."
  [element height]
  (log/info "resize-finish-method " height)
  (reset! diag {:element element :height height}))




