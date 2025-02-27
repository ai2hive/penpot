;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) UXBOX Labs SL

(ns app.util.forms
  (:refer-clojure :exclude [uuid])
  (:require
   [app.common.spec :as us]
   [app.util.dom :as dom]
   [app.util.i18n :refer [tr]]
   [cljs.spec.alpha :as s]
   [cuerdas.core :as str]
   [rumext.alpha :as mf]))

;; --- Handlers Helpers

(defn- interpret-problem
  [acc {:keys [path pred via] :as problem}]
  (cond
    (and (empty? path)
         (list? pred)
         (= (first (last pred)) 'cljs.core/contains?))
    (let [path (conj path (last (last pred)))]
      (assoc-in acc path {:code ::missing :type :builtin}))

    (and (seq path)
         (seq via))
    (assoc-in acc path {:code (last via) :type :builtin})

    :else acc))

(declare create-form-mutator)

(defn use-form
  [& {:keys [initial] :as opts}]
  (let [state      (mf/useState 0)
        render     (aget state 1)

        get-state  (mf/use-callback
                    (mf/deps initial)
                    (fn []
                      {:data (if (fn? initial) (initial) initial)
                       :errors {}
                       :touched {}}))

        state-ref  (mf/use-ref (get-state))
        form       (mf/use-memo (mf/deps initial) #(create-form-mutator state-ref render get-state opts))]

    (mf/use-effect
     (mf/deps initial)
     (fn []
       (if (fn? initial)
         (swap! form update :data merge (initial))
         (swap! form update :data merge initial))))

    form))

(defn- wrap-update-fn
  [f {:keys [spec validators]}]
  (fn [& args]
    (let [state    (apply f args)
          cleaned  (s/conform spec (:data state))
          problems (when (= ::s/invalid cleaned)
                     (::s/problems (s/explain-data spec (:data state))))

          errors   (merge (reduce interpret-problem {} problems)
                          (reduce (fn [errors vf]
                                    (merge errors (vf (:data state))))
                                  {} validators)
                          (:errors state))]

      (assoc state
             :errors errors
             :clean-data (when (not= cleaned ::s/invalid) cleaned)
             :valid (and (empty? errors)
                         (not= cleaned ::s/invalid))))))

(defn- create-form-mutator
  [state-ref render get-state opts]
  (reify
    IDeref
    (-deref [_]
      (mf/ref-val state-ref))

    IReset
    (-reset! [_ new-value]
      (if (nil? new-value)
        (mf/set-ref-val! state-ref (get-state))
        (mf/set-ref-val! state-ref new-value))
      (render inc))

    ISwap
    (-swap! [_ f]
      (let [f (wrap-update-fn f opts)]
        (mf/set-ref-val! state-ref (f (mf/ref-val state-ref)))
        (render inc)))


    (-swap! [_ f x]
      (let [f (wrap-update-fn f opts)]
        (mf/set-ref-val! state-ref (f (mf/ref-val state-ref) x))
        (render inc)))

    (-swap! [_ f x y]
      (let [f (wrap-update-fn f opts)]
        (mf/set-ref-val! state-ref (f (mf/ref-val state-ref) x y))
        (render inc)))

    (-swap! [_ f x y more]
      (let [f (wrap-update-fn f opts)]
        (mf/set-ref-val! state-ref (apply f (mf/ref-val state-ref) x y more))
        (render inc)))))

(defn on-input-change
  ([form field]
   (on-input-change form field false))
  ([form field trim?]
  (fn [event]
    (let [target (dom/get-target event)
          value  (if (or (= (.-type target) "checkbox")
                         (= (.-type target) "radio"))
                   (.-checked target)
                   (dom/get-value target))]
      (swap! form (fn [state]
                    (-> state
                        (assoc-in [:data field] (if trim? (str/trim value) value))
                        (update :errors dissoc field))))))))

(defn on-input-blur
  [form field]
  (fn [_]
    (let [touched (get @form :touched)]
      (when-not (get touched field)
        (swap! form assoc-in [:touched field] true)))))

;; --- Helper Components

(mf/defc field-error
  [{:keys [form field type]
    :as props}]
  (let [{:keys [message] :as error} (get-in form [:errors field])
        touched? (get-in form [:touched field])
        show? (and touched? error message
                   (cond
                     (nil? type) true
                     (keyword? type) (= (:type error) type)
                     (ifn? type) (type (:type error))
                     :else false))]
    (when show?
      [:ul.form-errors
       [:li {:key (:code error)} (tr (:message error))]])))

(defn error-class
  [form field]
  (when (and (get-in form [:errors field])
             (get-in form [:touched field]))
    "invalid"))

;; --- Form Specs and Conformers

(s/def ::email ::us/email)
(s/def ::not-empty-string ::us/not-empty-string)
(s/def ::color ::us/color)
