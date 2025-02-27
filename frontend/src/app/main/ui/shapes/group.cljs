;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) UXBOX Labs SL

(ns app.main.ui.shapes.group
  (:require
   [app.main.ui.context :as muc]
   [app.main.ui.shapes.mask :refer [mask-url clip-url mask-factory]]
   [app.util.object :as obj]
   [rumext.alpha :as mf]))

(defn group-shape
  [shape-wrapper]
  (let [render-mask (mask-factory shape-wrapper)]
    (mf/fnc group-shape
      {::mf/wrap-props false}
      [props]
      (let [shape          (unchecked-get props "shape")
            childs         (unchecked-get props "childs")
            render-id      (mf/use-ctx muc/render-ctx)
            masked-group?  (:masked-group? shape)

            [mask childs]  (if masked-group?
                             [(first childs) (rest childs)]
                             [nil childs])

            ;; We need to separate mask and clip into two because a bug in Firefox
            ;; breaks when the group has clip+mask+foreignObject
            ;; Clip and mask separated will work in every platform
            ;  Firefox bug: https://bugzilla.mozilla.org/show_bug.cgi?id=1734805
            [clip-wrapper clip-props]
            (if masked-group?
              ["g" (-> (obj/new)
                       (obj/set! "clipPath" (clip-url render-id mask)))]
              [mf/Fragment nil])

            [mask-wrapper mask-props]
            (if masked-group?
              ["g" (-> (obj/new)
                       (obj/set! "mask"     (mask-url render-id mask)))]
              [mf/Fragment nil])]

        [:> clip-wrapper clip-props
         [:> mask-wrapper mask-props
          (when masked-group?
            [:> render-mask #js {:mask mask}])

          (for [item childs]
            [:& shape-wrapper {:shape item
                               :key (:id item)}])]]))))



