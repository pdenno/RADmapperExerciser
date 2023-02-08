(ns rm-exerciser.app.components.share
  (:require
   ["@mui/material/Stack$default" :as Stack]
   ["@mui/material/Divider$default" :as Divider]
   [applied-science.js-interop :as j]
   [helix.core :refer [defnc $]]
   [helix.hooks :as hooks]
   [rm-exerciser.app.util :as util]
   [taoensso.timbre :as log :refer-macros [info debug log]]))

;;; ToDo:
;;;   (1) The on-resize-x methods should not be necessary. It should be the case that resizing the parent
;;;       resizes the child too. So far, this is not the case with CodeMirror editor, but see
;;;       https://discuss.codemirror.net/t/editor-variable-height/3523. Might not be true of mui/TextArea either.
;;;   (2) Is there a way to factor out start-drag, stop-drag, and drag methods?
;;;   (3) pct-up on ShareUpDown.

(def diag (atom nil))
(def mouse-down? "Should not be necessary, but it appears it is." (atom false))

(defn resize
  "Set dimension of parent the EditorView."
  [parent width height]
  ;(log/info "resize default: parent = " (j/get parent :id) "height = " height)
  (when width  (j/assoc-in! parent [:style :width]  (str width  "px")))
  (when height (j/assoc-in! parent [:style :height] (str height "px"))))

(defnc ShareUpDown
  "Create a Stack with two children (props :up and :down) where the
   area shared between the two can be distributed by dragging the Stack divider,
   a black bar that could be viewed as the frame dividing the two."
  [{:keys [up dn init-height on-resize-up on-resize-dn on-stop-drag]
    :or {on-resize-up resize on-resize-dn resize init-height 300}}]
  {:helix/features {:check-invalid-hooks-usage true}}
  (let [[up-height set-up-height]     (hooks/use-state (int (/ init-height 2)))
        [dn-height set-dn-height]     (hooks/use-state (int (/ init-height 2)))
        [parent-dims set-parent-dims] (hooks/use-state {:height init-height :ubound nil :dbound nil})
        u-div (hooks/use-ref nil)
        d-div (hooks/use-ref nil)]
    (letfn [(set-dims [mouse-y] ; Essentially, puts border at mouse-y
              (when-let [udiv (j/get u-div :current)]
                (when-let [ddiv (j/get d-div :current)]
                  (let [{:keys [ubound dbound height]} parent-dims
                        up-fraction (- 1.0 (/ (- dbound mouse-y) (- dbound ubound)))
                        up-size (* up-fraction  height)
                        dn-size (* (- 1 up-fraction) height)]
                    (when (<= ubound mouse-y dbound)
                      (set-up-height up-size)
                      (set-dn-height dn-size)
                      (on-resize-up udiv nil up-size)
                      (on-resize-dn ddiv nil dn-size)
                      #_(log/info "upsize = " up-size " dn-size = " dn-size))))))
            (do-drag [e] (when @mouse-down?
                           #_(log/info "mouse-y :" (j/get e :clientY))
                           (-> e (j/get :clientY) set-dims)))
            (start-drag [_e]
              (reset! mouse-down? true)
              (js/document.addEventListener "mouseup"   stop-drag)
              (js/document.addEventListener "mousemove" do-drag))
            (stop-drag []
              (reset! mouse-down? false)
              (when-let [udiv (j/get u-div :current)]
                (when-let [ddiv (j/get d-div :current)]
                  (when on-stop-drag
                    (on-stop-drag udiv up-height)
                    (on-stop-drag ddiv dn-height))))
              (js/document.removeEventListener "mouseup"   stop-drag)
              (js/document.removeEventListener "mousemove" do-drag))]
      (hooks/use-effect [] ; set-parent-dims
        (when-let [udiv (j/get u-div :current)]
          (when-let [ddiv (j/get d-div :current)]
            (let [ubound (j/get (.getBoundingClientRect udiv) :top)
                  dbound (+ ubound init-height)
                  height (int (- (/ init-height 2) 2))]
              (when on-stop-drag
                (on-stop-drag udiv height)
                (on-stop-drag ddiv height))
              (set-parent-dims {:height init-height :ubound ubound :dbound dbound})
              (on-resize-up udiv nil height)
              (on-resize-dn ddiv nil height)
              #_(log/info "use-effect setting border in middle: " (+ ubound (/ init-height 2)))))))
      ($ Stack
         {:direction "column" :display "flex" :width "100%":height "100%" :alignItems "stretch" :spacing 0
          :divider ($ Divider {:variant "activeHoriz" :color "black"
                               :onMouseDown start-drag :onMouseMove do-drag :onMouseUp stop-drag})}
         ($ "div" {:ref u-div :height up-height :id "up-div"}
            up)
         ($ "div" {:ref d-div :height dn-height :id "dn-div"}
            dn)))))

(defnc ShareLeftRight
  "Create a Stack with two children (props :up and :down) where the
   area shared between the two can be distributed by dragging the Stack divider,
   a black bar that could be viewed as the frame dividing the two."
  [{:keys [left right init-width on-resize-left on-resize-right lf-pct on-stop-drag]
    :or {on-resize-left resize on-resize-right resize init-width 800 lf-pct 0.50}}]
  {:helix/features {:check-invalid-hooks-usage true}}
  (let [[lf-width set-lf-width]       (hooks/use-state (int (*      lf-pct  (/ init-width 2))))
        [rt-width set-rt-width]       (hooks/use-state (int (* (- 1 lf-pct) (/ init-width 2))))
        [parent-dims set-parent-dims] (hooks/use-state {:width init-width :lbound nil :rbound nil})
        l-div (hooks/use-ref nil)
        r-div (hooks/use-ref nil)]
    (letfn [(set-dims [mouse-x] ; Essentially, puts border at mouse-x
              (when-let [ldiv (j/get l-div :current)]
                (when-let [rdiv (j/get r-div :current)]
                  (let [{:keys [lbound rbound width]} parent-dims
                        lf-fraction (- 1.0 (/ (- rbound mouse-x) (- rbound lbound)))
                        lf-size (* lf-fraction  width)
                        rt-size (* (- 1 lf-fraction) width)]
                    (when (<= lbound mouse-x rbound)
                      (set-lf-width lf-size)
                      (set-rt-width rt-size)
                      (on-resize-left  ldiv lf-size nil)
                      (on-resize-right rdiv rt-size nil)
                      #_(log/info "lfsize = " lf-size " rt-size = " rt-size))))))
            (do-drag [e] (when @mouse-down? (-> e (j/get :clientX) set-dims)))
            (start-drag [_e]
              (reset! mouse-down? true)
              (js/document.addEventListener "mouseup"   stop-drag)
              (js/document.addEventListener "mousemove" do-drag))
            (stop-drag []
              (reset! mouse-down? false)
              (js/document.removeEventListener "mouseup"   stop-drag)
              (js/document.removeEventListener "mousemove" do-drag))]
      (hooks/use-effect []
        (when-let [ldiv (j/get l-div :current)]
          (when-let [rdiv (j/get r-div :current)]
            (let [parent (j/get ldiv :parentNode)
                  lbound (j/get (.getBoundingClientRect parent) :left)
                  rbound (+ lbound init-width)
                  left-init  (int (*      lf-pct   (- init-width 2.5)))   ; minus 2.5 is border, roughly.
                  right-init (int (* (- 1 lf-pct)  (- init-width 2.5)))
                  height (j/get ldiv :clientHeight)]
              (when on-stop-drag
                (on-stop-drag ldiv height)
                (on-stop-drag rdiv height))
              (set-parent-dims {:width init-width :lbound lbound :rbound rbound})
              (on-resize-left  ldiv left-init nil)
              (on-resize-right rdiv right-init nil)
              #_(log/info "use-effect: left-width = " left-init " right-init = " right-init)))))
      ($ Stack
         {:direction "row" :display "flex" :width "100%":height "100%" :alignItems "stretch" :spacing 0
          :divider ($ Divider {:variant "activeVert" :color "black"
                               :onMouseDown start-drag :onMouseMove do-drag :onMouseUp stop-drag})}
         ($ "div" {:ref l-div
                   :width lf-width
                   :id "lf-div"
                   :style {:border-style "none none solid solid"}}
            left)
         ($ "div" {:ref r-div :width rt-width :id "rt-div" :style {:border-style "none solid solid none"}}
            right)))))
