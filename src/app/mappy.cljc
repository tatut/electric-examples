(ns app.mappy
  #?(:cljs (:require-macros app.mappy))
  (:require #?(:clj [datascript.core :as d]) ; database on server
            #?(:clj [clojure.data.csv :as csv])
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]
            #?(:cljs ["@openlayers-elements/core/ol-map" :as ol-map])
            #?(:cljs ["@openlayers-elements/maps/ol-layer-openstreetmap" :as ol-layer-openstreetmap])
            #?(:cljs ["@openlayers-elements/core/ol-layer-vector" :as ol-layer-vector])
            #?(:cljs ["@openlayers-elements/maps/ol-marker-icon" :as ol-marker-icon])
            #?(:cljs ["ol/proj" :as ol-proj])))

(defonce !conn #?(:clj (d/create-conn {}) :cljs nil)) ; database on server
(e/def db) ; injected database ref; Electric defs are always dynamic)

;; Import stops from Oulu (or any) GTFS CSV
#?(:clj (def gtfs-stops-file "stops.txt"))
#?(:clj (defn import-stops []
          (d/transact! !conn
                       (vec
                        (for [[id _ name lat lon & _]
                              (rest (csv/read-csv (slurp gtfs-stops-file)))
                              :when (and id name lat lon)]

                          {:stop/id id
                           :stop/name name
                           :stop/lat (Double/parseDouble lat)
                           :stop/lon (Double/parseDouble lon)})))))

#?(:clj (defn find-stops [db [lon-min lat-min lon-max lat-max :as ex]]
          (if-not (every? number? ex)
            []
            (try
              (d/q '[:find [(pull ?s [:stop/id :stop/name :stop/lat :stop/lon]) ...]
                     :where
                     [?s :stop/lat ?lat] [(>= ?lat lat-min)] [(<= ?lat lat-max)]
                     [?s :stop/lon ?lon] [(>= ?lon lon-min)] [(<= ?lon lon-max)]
                     :in $ lon-min lat-min lon-max lat-max]
                   db lon-min lat-min lon-max lat-max)
              (catch Exception e
                [])))))

(e/defn StopMarker [S]
  (e/server
   (let [{:stop/keys [id name lat lon]} S]
     (e/client
      (dom/element :ol-marker-icon
                   (dom/props {:id id :lat lat :lon lon :src "bus.png"}))))))
(e/defn Mappy []
  (e/server
   (binding [db (e/watch !conn)]
     (e/client
      (let [!extent (atom nil)
            extent (e/watch !extent)]
        (dom/div
         (dom/text (str "Extent " (pr-str extent) " => " (e/server (count (find-stops db extent))) " stops"))
         (dom/div (dom/props {:style {:width "100%" :height "500px"}})
                  (dom/element :ol-map (dom/props {:style {:height "500px"}
                                                   :zoom "9" :lat "65.0" :lon "24.73"})
                               (dom/element :ol-layer-openstreetmap)
                               (dom/on "view-change"
                                       (e/fn [event]
                                         (reset! !extent
                                                 (-> event .-target .-map .getView .calculateExtent
                                                     (ol-proj/transformExtent "EPSG:3857" "EPSG:4326")))))

                               (dom/element :ol-layer-vector
                                            (e/server
                                             (e/for-by :stop/id [stop (find-stops db extent)]
                                               (StopMarker. stop))))))))))))
