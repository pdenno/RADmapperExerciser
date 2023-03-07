(ns rm-exerciser.app.components.save-modal
  (:require
   [applied-science.js-interop :as j]
   [helix.core :refer [defnc $]]
   [helix.hooks :as hooks]
   ["@mui/material/Box$default" :as Box]
   ["@mui/material/IconButton$default" :as IconButton]
   ["@mui/icons-material/Save$default" :as Save]
   ["@mui/material/Typography$default" :as Typography]
   ["@mui/material/Modal$default" :as Modal]
   ["superagent" :as request]))

(def style (clj->js
            {:position "absolute", ; as 'absolute'
             :top "50%",
             :left "50%",
             :transform "translate(-50%, -50%)",
             :width 800,
             :bgcolor "background.paper",
             :border "2px solid #000",
             :boxShadow 24,
             :p 2}))

(def white-style (clj->js {:color "background.paper"})),

(defnc SaveModal [{:keys [code-fn data-fn svr-prefix]}]
  (let [[open, set-open] (hooks/use-state false)
        [url, set-url] (hooks/use-state "[not set]")]
    (letfn [(handle-save []
              (set-open true)
              (let [code (code-fn)
                    data (data-fn)]
                (-> (request "POST" (str svr-prefix "/api/example"))
                    (.send (clj->js {:code code :data data}))
                    (.then    #(when-let [id (-> % (j/get :body) (j/get :save-id))]
                                 (let [save-id (str svr-prefix "/api/example?id=" id)]
                                   (js/console.log "save-id = " save-id)
                                   (set-url save-id))))
                    (.catch   #(js/console.log "catch = " %))
                    (.finally (fn [_] nil)))))
            (handle-close [] (set-open false))]
      ($ "div"
         ($ IconButton {:onClick handle-save} ($ Save {:sx white-style}))
         ($ Modal {:open open
                   :onClose handle-close
                   :aria-labelledby "save-modal-title"
                   :aria-describedby "save-modal-description"}
            ($ Box {:sx style}
               ($ Typography {:id "save-modal-title" :variant "h6" :component "h6"}
                  "The example can be found at this URL:")
               ($ Typography {:id "save-modal-description" :sx {:mt 20}} url)))))))


;;; https://mui.com/material-ui/react-modal/
;;; import * as React from 'react';
;;; import Box from '@mui/material/Box';
;;; import Button from '@mui/material/Button';
;;; import Typography from '@mui/material/Typography';
;;; import Modal from '@mui/material/Modal';
;;;
;;; export default function BasicModal() {
;;;   const [open, setOpen] = React.useState(false);
;;;   const handleOpen = () => setOpen(true);
;;;   const handleClose = () => setOpen(false);
;;;
;;;   return (
;;;     <div>
;;;       <Button onClick={handleOpen}>Open modal</Button>
;;;       <Modal
;;;         open={open}
;;;         onClose={handleClose}
;;;         aria-labelledby="modal-modal-title"
;;;         aria-describedby="modal-modal-description"
;;;       >
;;;         <Box sx={style}>
;;;           <Typography id="modal-modal-title" variant="h6" component="h2">
;;;             Text in a modal
;;;           </Typography>
;;;           <Typography id="modal-modal-description" sx={{ mt: 2 }}>
;;;             Duis mollis, est non commodo luctus, nisi erat porttitor ligula.
;;;           </Typography>
;;;         </Box>
;;;       </Modal>
;;;     </div>
;;;   );
;;; }
