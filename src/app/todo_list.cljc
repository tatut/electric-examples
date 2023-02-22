(ns app.todo-list

  ; trick shadow into ensuring that client/server always have the same version
  ; all .cljc files containing Electric code must have this line!
  #?(:cljs (:require-macros app.todo-list; <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
                            [contrib.element-syntax :refer [<%]]))

  (:require #?(:clj [datascript.core :as d]) ; database on server
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]
            [clojure.string :as str])
  )

(defonce !conn #?(:clj (d/create-conn {}) :cljs nil)) ; database on server
(e/def db) ; injected database ref; Electric defs are always dynamci



#?(:clj
   (defn create-todo! [description]
     (d/transact! !conn [{:task/description description :task/status :active}])))


#?(:clj (defn todo-count [db]
          (count
            (d/q '[:find [?e ...] :in $ ?status
                   :where [?e :task/status ?status]] db :active))))

#?(:clj (defn todo-records [db]
          (->> (d/q '[:find [(pull ?e [:db/id :task/description]) ...]
                      :where [?e :task/status]] db)
            (sort-by :task/description))))

#?(:clj (defn tyyli  []
          (println "palautetaan tyyli")
          {:background-color (rand-nth ["red" "green" "blue" "black" "yellow"])}))



(e/defn TodoItem [id]
  (e/server
    (let [e (d/entity db id)
          status (:task/status e)]
      (e/client
       (<% :div.m-2 {::dom/class (when (= status :active) "font-bold")}
           (ui/checkbox
            (case status :active false, :done true)
            (e/fn [v]
              (e/server
               (e/discard
                (d/transact! !conn [{:db/id id
                                     :task/status (if v :done :active)}]))))
            (dom/props {:id id}))
           (dom/label (dom/props {:for id}) (dom/text (e/server (:task/description e)))))))))

(e/defn InputSubmit [F]
  ; Custom input control using lower dom interface for Enter handling
  (<% :input {:placeholder "Buy milk"
              :on-keydown (e/fn [e]
                            (when (= "Enter" (.-key e))
                              (when-some [v (contrib.str/empty->nil (-> e .-target .-value))]
                                (new F v)
                                (set! (.-value dom/node) ""))))}))

(e/defn TodoCreate []
  (e/client
   (InputSubmit. (e/fn [v]
                   (e/server
                    (e/discard (create-todo! v)))))))

(e/defn Todo-list []
  (e/server
   (binding [db (e/watch !conn)]
     (e/client
      (<% :link {:rel :stylesheet :href "/todo-list.css"})
      (<% :h1 {:style {:font-size "20px"}
               :on-click (e/fn [evt]
                           (js/console.log "EVENTTI " evt)
                            (e/discard (e/server (println "tapahtui"))))}
          "minimal todo list")
      (<% :p "it's multiplayer, try two tabs")
      (<% :button {:on-click (fn [evt] (js/alert "JOO" evt))} "jotain")
      (<% :div.todo-list
          (TodoCreate.)
          (<% :div.todo-items
              (e/server
               (e/for-by :db/id [{:keys [db/id]} (todo-records db)]
                         (TodoItem. id))))
          (<% :p.counter
              (<% :span.count (dom/text (e/server (todo-count db))))
              " items left"))))))

(e/defn Debug []
  (dom/div "DEBUG:"
           (dom/text (e/server (pr-str hyperfiddle.api/*http-request*)))))
