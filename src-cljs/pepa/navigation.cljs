(ns pepa.navigation
  (:require [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [cljs.core.async :as async]
            [clojure.string :as s]
            [cljs.reader :as reader]

            [om.core :as om]
            [pepa.data :as data]
            [goog.string :as gstring])
  (:require-macros [cljs.core.match.macros :refer [match]])
  (:import goog.History
           goog.history.EventType
           goog.crypt
           goog.crypt.base64))

(defn navigate! [route & [ignore-history]]
  (if-not ignore-history
    (set! (-> js/window .-location .-hash) route)
    (do (js/window.history.replaceState nil "" route)
        (secretary/dispatch! (.substring route 1)))))

(defn navigation-ref []
  (some-> data/state
          (om/root-cursor)
          :navigation
          (om/ref-cursor)))

(defn dashboard-route [& [query-params]]
  (secretary/render-route "/" {:query-params query-params}))

(defn workflow-route [workflow]
  (secretary/render-route (str "/" (name workflow))))

(secretary/defroute document-route "/document/:id" [id query-params]
  (om/update! (navigation-ref)
              {:route [:document (js/parseInt id)]
               :query-params query-params}))

(defn tag-search [tag & [query-params]]
  (->
   (str "/search/tag/" (gstring/urlEncode (s/lower-case tag)))
   (secretary/render-route {:query-params query-params})))

(defn full-search [query & [query-params]]
  (assert (string? query))
  (->
   (str "/search/" (gstring/urlEncode query))
   (secretary/render-route {:query-params query-params})))

(defn nav->route [navigation]
  (let [{:keys [route query-params]} (om/value navigation)]
   (match [route]
     [:dashboard]
     (dashboard-route query-params)

     [[:document id]]
     (document-route {:id id, :query-params query-params})

     [[:search [:tag tag]]]
     (tag-search tag query-params)

     [[:search [:query query]]]
     (full-search query query-params))))

;;; Routes

(secretary/defroute "/" [query-params]
  (om/update! (navigation-ref)
              {:route :dashboard
               :query-params query-params}))

;;; Default Route
(secretary/defroute "" [query-params]
  (om/update! (navigation-ref)
              {:route :dashboard
               :query-params query-params}))

(secretary/defroute "/:workflow" [workflow query-params]
  ;; TODO: Check for available workflows
  (om/update! (navigation-ref)
              {:route (keyword workflow)
               :query-params query-params}))

(secretary/defroute "/search/tag/:tag" [tag query-params]
  (om/update! (navigation-ref)
              {:route [:search [:tag (gstring/urlDecode tag)]]
               :query-params query-params}))

(secretary/defroute "/search/:query" [query query-params]
  (om/update! (navigation-ref)
              {:route [:search [:query query]]
               :query-params query-params}))

(secretary/set-config! :prefix "#")

(defonce ^:private history
  (let [h (History.)]
    (goog.events/listen h EventType.NAVIGATE #(secretary/dispatch! (.-token %)))
    (doto h
      (.setEnabled true))))