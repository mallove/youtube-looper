(ns youtube-looper.views-cards
  (:require [cljs.test :refer-macros [is async]]
            [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [youtube-looper.next.parser :as p])
  (:require-macros [devcards.core :as dc :refer [defcard deftest]]))

(defn pd [f]
  (fn [e]
    (.preventDefault e)
    (f)))

(defn call-computed [c name & args]
  (when-let [f (om/get-computed c name)]
    (apply f args)))

(defui LoopRow
  static om/IQuery
  (query [this]
    [:loop/label :loop/start :loop/finish])

  Object
  (render [this]
    (let [{:keys [:loop/label :loop/start :loop/finish] :as loop} (-> this om/props)]
      (dom/div nil
        (dom/div nil label)
        (dom/div nil start)
        (dom/div nil finish)
        (dom/a #js {:href "#" :onClick (pd #(call-computed this :on-delete))} "Delete")))))

(def loop-row (om/factory LoopRow))

(defn state-input [c key]
  (let [value (om/get-state c key)]
    (dom/input #js {:value    value
                    :onChange #(let [input-value (.. % -target -value)]
                                (om/update-state! c merge {key input-value}))})))

(defui NewLoopForm
  Object
  (render [this]
          (let [{:keys [on-submit]} (om/props this)]
            (dom/div nil
              (state-input this :loop/start)
              (state-input this :loop/finish)
              (dom/button #js {:onClick
                               #(do
                                 (on-submit (om/get-state this))
                                 (om/set-state! this {:loop/start "" :loop/finish ""}))}
                          "Add Loop")))))

(def new-loop-form (om/factory NewLoopForm))

(defn create-loop [c loop]
  (let [id (-> c om/props :youtube/id)]
    (om/transact! c `[(track/new-loop {:loop ~loop :youtube/id ~id})])))

(defn delete-loop [c loop]
  (let [id (-> c om/props :youtube/id)]
    (om/transact! c `[(track/remove-loop {:loop ~loop :youtube/id ~id})])))

(defui LoopManager
  static om/IQuery
  (query [this]
    [:youtube/id
     {:track/loops (om/get-query LoopRow)}])
  
  static om/Ident
  (ident [this {:keys [youtube/id]}]
    [:tracks/by-youtube-id id])
 
  Object
  (render [this]
    (let [{:keys [track/loops]} (om/props this)]
      (dom/div nil
        (apply dom/div nil (->> (map #(om/computed % {:on-delete (partial delete-loop this %)}) loops)
                                (map loop-row)))
        (new-loop-form {:on-submit #(create-loop this %)})))))

(def loop-manager (om/factory LoopManager))

(defui LoopPage
  static om/IQuery
  (query [this] [{:app/current-track (om/get-query LoopManager)}])
  
  Object
  (render [this]
    (let [{:keys [app/current-track] :as props} (om/props this)]
      (loop-manager current-track))))

(def loop-page (om/factory LoopPage))

(def init-data {})

(def fake-store
  (p/map-kv-store {"123" {:youtube/id  "123"
                          :track/loops #{{:loop/label "full" :loop/start 5 :loop/finish 200}}}}))

(def reconciler
  (om/reconciler
    {:state  init-data
     :parser p/parser
     :send   (fn [query cb]
               (cb (p/remote-parser {:current-track #(str "123")
                                     :store         fake-store}
                                    (:remote query))))}))

(def reconciler-local-storage
  (om/reconciler
    {:state  init-data
     :parser p/parser
     :send   (fn [query cb]
               (println "REMOTE" query)
               (cb (p/remote-parser {:current-track #(str "123")
                                     :store         (p/local-storage-kv-store "cards-")}
                                    (:remote query))))}))

(defcard loop-row-sample
  (loop-row {:loop/start 123
             :loop/finish 128
             :loop/label "Sample"
             :loop/id 1}))

(defcard loop-page-card
  "Display the loop manager dialog"
  (om/mock-root reconciler LoopPage))

(defcard loop-page-card-local-storage
  "Display the loop manager dialog using local storage."
  (om/mock-root reconciler-local-storage LoopPage))
