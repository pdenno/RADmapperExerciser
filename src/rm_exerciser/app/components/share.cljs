(ns rm-exerciser.app.components.share
  (:require
   ["@mui/material/Stack$default" :as Stack]
   ["@mui/material/Divider$default" :as Divider]
   [applied-science.js-interop :as j]
   [helix.core :refer [defnc $]]
   [helix.hooks :as hooks]
   [taoensso.timbre :as log :refer-macros [info debug log]]))

(def diag (atom nil))

(defn resize-default
  "Set dimension of parent the EditorView."
  [parent width height]
  ;(log/info "resize default: parent = " (j/get parent :id) "height = " height)
  (when width  (j/assoc-in! parent [:style :width]  (str width  "px")))
  (when height (j/assoc-in! parent [:style :height] (str height "px"))))

(defnc ShareUpDown
  "Create a Stack with two children (props :up and :down) where the
   area shared between the two can be distributed by dragging the Stack divider,
   a black bar that could be viewed as the frame dividing the two."
  [{:keys [up dn init-height on-resize-up on-resize-dn]
    :or {on-resize-up resize-default on-resize-dn resize-default init-height 300}}]
  {:helix/features {:check-invalid-hooks-usage true}}
  ;; ToDo: Get the parent's height here or pass it in.
  (let [[up-height set-up-height]  (hooks/use-state (int (/ init-height 2)))
        [dn-height set-dn-height]  (hooks/use-state (int (/ init-height 2)))
        [parent-height]            (hooks/use-state init-height)
        u-ref (hooks/use-ref nil)
        d-ref (hooks/use-ref nil)
        mouse-down? (atom false)] ; ToDo: Why is this necessary? (It is necessary.)
    (letfn [(parent-dims []
              (when-let [uparent (j/get u-ref :current)]
                (when-let [dparent (j/get d-ref :current)]
                  (let [parent (j/get uparent :parentNode)
                        ubound (j/get (.getBoundingClientRect parent) :top)
                        dbound (j/get (.getBoundingClientRect parent) :bottom)
                        height (- parent-height 5)] ; minus divider.
                    {:ubound ubound :dbound dbound :height height}))))
            (set-dims [mouse-y]
              (when-let [uparent (j/get u-ref :current)]
                (when-let [dparent (j/get d-ref :current)]
                  (let [{:keys [ubound dbound height]} (parent-dims)
                        up-fraction (- 1.0 (/ (- dbound mouse-y) (- dbound ubound)))
                        up-size (* up-fraction  height)
                        dn-size (* (- 1 up-fraction) height)]
                    (when (<= ubound mouse-y dbound)
                      (set-up-height up-size)
                      (set-dn-height dn-size)
                      (on-resize-up uparent nil up-size)
                      (on-resize-dn dparent nil dn-size)
                      #_(reset! diag {:ubound ubound :dbound dbound :height height :uparent uparent :dparent dparent}))))))
            (do-drag [e] (reset! diag e) (when @mouse-down? (-> e (j/get :clientY) set-dims)))
            (start-drag [_e]
              (reset! mouse-down? true)
              (js/document.addEventListener "mouseup"   stop-drag)
              (js/document.addEventListener "mousemove" do-drag))
            (stop-drag []
              (reset! mouse-down? false)
              (js/document.removeEventListener "mouseup"   stop-drag)
              (js/document.removeEventListener "mousemove" do-drag))]
      (hooks/use-effect []
        (when-let [uparent (j/get u-ref :current)]
          (when-let [dparent (j/get d-ref :current)]
            (let [parent (j/get uparent :parentNode)
                  ubound (j/get (.getBoundingClientRect parent) :top)
                  dbound (j/get (.getBoundingClientRect parent) :bottom)]
              (set-dims (/ (- dbound ubound) 2))))))
      ($ Stack
         {:direction "column" :display "flex" :width "100%":height "100%" :alignItems "stretch" :spacing 0
          :divider ($ Divider {:variant "active-horiz" :height 5 :color "black"
                               :onMouseDown start-drag :onMouseMove do-drag :onMouseUp stop-drag})}
         ($ "div" {:ref u-ref :height up-height :id "up-div"}
            up)
         ($ "div" {:ref d-ref :height dn-height :id "dn-div"}
            dn)))))

(defnc ShareLeftRight
  "Create a Stack with two children (props :left and :right) where the
   area shared between the two can be distributed by dragging the Stack divider,
   a black bar that could be viewed as the frame dividing the two.
   Optional :height (defaults to 300px) can be used to give height other than
   that needed to fit the children. "
  [{:keys [left right init-left init-right] :or {init-left "60%" init-right "40%"}}]
  {:helix/features {:check-invalid-hooks-usage true}}
  (let [[lwidth set-lwidth] (hooks/use-state {:size init-left})
        [rwidth set-rwidth] (hooks/use-state {:size init-right})
        l-ref (hooks/use-ref nil)
        r-ref (hooks/use-ref nil)
        mouse-down? (atom false)]
    (letfn [(do-drag [e]
              (when @mouse-down?
                (when-let [rbox (j/get r-ref :current)]
                  (when-let [lbox (j/get l-ref :current)]
                    (let [lbound (j/get (.getBoundingClientRect lbox) :left)
                          rbound (j/get (.getBoundingClientRect rbox) :right)
                          mouse-x (j/get e :clientX)]
                      (when (<  lbound mouse-x rbound)
                        (let [lpct (int (* 100 (/ (- mouse-x lbound) (- rbound lbound))))]
                          (set-lwidth {:size (str lpct "%")})
                          (set-rwidth {:size (str (- 100 lpct) "%")}))))))))
            (start-drag [_e]
              (reset! mouse-down? true)
              (js/document.addEventListener "mouseup"   stop-drag)
              (js/document.addEventListener "mousemove" do-drag))
            (stop-drag []
              (reset! mouse-down? false)
              (js/document.removeEventListener "mouseup"   stop-drag)
              (js/document.removeEventListener "mousemove" do-drag))]
      ($ Stack
         {:direction "row" :display "flex" :spacing 0 :alignItems "stretch" ; does nothing
          :divider ($ Divider {:variant "active-vert" :width 5 :color "black"
                               :onMouseDown start-drag :onMouseMove do-drag :onMouseUp stop-drag})}
         ($ "div" {:ref l-ref :width (:size lwidth)}
            left)
         ($ "div" {:ref r-ref :width (:size rwidth)}
            right)))))
