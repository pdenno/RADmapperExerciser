(ns rm-exerciser.app.components.share
  (:require
   ["@mui/material/Stack$default" :as Stack]
   ["@mui/material/Divider$default" :as Divider]
   ["@mui/material/Box$default" :as MuiBox]
   [applied-science.js-interop :as j]
   [helix.core :refer [defnc $]]
   [helix.hooks :as hooks]))

(def diag (atom nil))

(defnc ShareUpDown
  "Create a Stack with two children (props :up and :down) where the
   area shared between the two can be distributed by dragging the Stack divider,
   a black bar that could be viewed as the frame dividing the two."
  [{:keys [up down]}]
  {:helix/features {:check-invalid-hooks-usage true}}
  (let [[up-height set-up-height] (hooks/use-state {:size 200 #_(- (j/get up-bounds :bottom) (j/get up-bounds :top))})
        [dn-height set-dn-height] (hooks/use-state {:size 200})
        u-ref (hooks/use-ref nil)
        d-ref (hooks/use-ref nil)
        mouse-down? (atom false)] ; ToDo: Why is this necessary? (It is necessary.)
    (letfn [(do-drag [e]
              (when @mouse-down?
                (when-let [ubox (j/get u-ref :current)]
                  (reset! diag {:u-ref u-ref :ubox ubox :up up})
                  (when-let [dbox (j/get d-ref :current)]
                    (let [ubound (j/get (.getBoundingClientRect ubox) :top)
                          dbound (j/get (.getBoundingClientRect dbox) :bottom)
                          height (- dbound ubound)
                          mouse-y (j/get e .-clientY)]
                      (when (<  ubound mouse-y dbound)
                        (let [up-fraction (- 1.0 (/ (- dbound mouse-y) (- dbound ubound)))
                              up-size (int (* up-fraction       height))
                              dn-size (int (* (- 1 up-fraction) height))]
                          (js/console.log "up-size = " up-size " dn-size = " dn-size)
                          (set-up-height {:size up-size})
                          (set-dn-height {:size dn-size}))))))))
            (start-drag [_e]
              (reset! mouse-down? true)
              (js/document.addEventListener "mouseup"   stop-drag)
              (js/document.addEventListener "mousemove" do-drag))
            (stop-drag []
              (reset! mouse-down? false)
              (js/document.removeEventListener "mouseup"   stop-drag)
              (js/document.removeEventListener "mousemove" do-drag))]
      ($ Stack
         {:direction "column"
          :display   "flex"
          :height    "100%"
          :alignItems "stretch" ; does nothing
          :width     "100%"
          :spacing   0
          :divider ($ Divider {:variant "active-horiz"
                               :height 5
                               :onMouseDown start-drag
                               :onMouseMove do-drag
                               :onMouseUp   stop-drag
                               :color "black"})}
         ($ MuiBox {:ref u-ref :height (:size up-height)}
            up)
         ($ MuiBox {:ref d-ref :height (:size dn-height)}
            down)))))

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
                          mouse-x (j/get e .-clientX)]
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
         {:direction "row"
          :display   "flex"
          #_#_:height    height ; If you use this, the divider will be too short
          :alignItems "stretch" ; does nothing
          #_#_:width     "100%"
          :spacing   0
          :divider ($ Divider {:variant "active-vert"
                               :width 5
                               :onMouseDown start-drag
                               :onMouseMove do-drag
                               :onMouseUp   stop-drag
                               :color "black"})}
         ($ MuiBox {:ref l-ref :width (:size lwidth)} left)
         ($ MuiBox {:ref r-ref :width (:size rwidth)} right)))))
