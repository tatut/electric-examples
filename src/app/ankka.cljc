(ns app.ankka
  "DuckDB demo"
  #?(:cljs (:require-macros app.ankka))
  (:require [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [missionary.core :as m]
            [hyperfiddle.electric-ui4 :as ui]
            [contrib.element-syntax :refer-macros [<%]]
            [hyperfiddle.electric-ui4 :as ui])
  #?(:clj (:import (java.sql DriverManager))))

#?(:clj (def driver-class (delay (Class/forName "org.duckdb.DuckDBDriver"))))

#?(:clj (def c (delay
                 @driver-class
                 (DriverManager/getConnection "jdbc:duckdb:"))))

#?(:clj (defn rq
          ([query]
           (rq conj [] query))
          ([reduce-fn query]
           (rq reduce-fn nil query))
          ([reduce-fn init query]
           (with-open [stmt (.createStatement @c)
                       rs (.executeQuery stmt query)]
             (let [rsmd (.getMetaData rs)
                   cols (map inc (range (.getColumnCount rsmd)))
                   names (mapv (fn [i]
                                 (.getColumnName rsmd i))
                               cols)]
               (loop [acc init]
                 (if (reduced? acc)
                   @acc
                   (if (.next rs)
                     (let [m (zipmap names (map #(.getObject rs %) cols))]
                       (recur (reduce-fn acc m)))
                     acc))))))))

#?(:clj (defn qf
          "query first"
          [query]
          (rq (fn [_ m] (reduced m)) query)))

(def tables {"Organizations" "organizations-2000000.csv"
             "People" "people-2000000.csv"})

(def page-size 10)

#?(:clj (defn columns [table-name]
          (sort (keys (qf (str "SELECT * FROM '" (tables table-name) "' LIMIT 1"))))))
#?(:clj (defn contents [table-name offset limit]
          (rq (str "SELECT * FROM '" (tables table-name) "'"
                   " OFFSET " offset
                   " LIMIT " limit))))
#?(:clj (defn row-count [table-name]
          (-> (qf (str "SELECT COUNT(*) FROM '" (tables table-name) "'")) first val)))

(e/defn TableContents [table-name]
  (e/client
   (let [!page (atom 0)
         page (e/watch !page)
         [columns total-rows] (e/server [(columns table-name) (row-count table-name)])
         last-page (dec (js/Math.ceil (/ total-rows page-size)))
         set-page-fn! (e/fn [p e]
                        (.preventDefault e)
                        (reset! !page p))]
     (<% :div
         (<% :table.table.table-zebra
             (<% :thead
                 (<% :tr
                     (e/for [k columns]
                       (<% :th (dom/text k)))))
             (<% :tbody
                 (e/for [row (e/server
                              (println "HAETAAN " (* page page-size) page-size)
                              (contents table-name (* page page-size) page-size))]
                   (<% :tr
                       (e/for [c columns]
                         (<% :td (dom/text (str (get row c)))))))))

         (<% :div.btn-group.m-4
             (<% :button.btn {:on-click (e/partial 2 set-page-fn! 0)} "<<")
             (<% :button.btn {:on-click (e/partial 2 set-page-fn! (dec page))} "<")
             (let [!insert-page (atom false)
                   insert-page (e/watch !insert-page)]
               (<% :button.btn {:on-click (e/fn [_] (reset! !insert-page true))}
                   (if insert-page
                     (<% :input.input.input-primary.text-black.w-24
                         {:on-change (e/fn [e]
                                       (->> e .-target .-value js/parseInt
                                            (reset! !page))
                                       (reset! !insert-page false))})
                     (dom/text page))))
             (<% :button.btn {:on-click (e/partial 2 set-page-fn! (inc page))} ">")
             (<% :button.btn {:on-click (e/partial 2 set-page-fn! last-page)} ">>"))))))

(e/defn Listing []
  (e/client
   (let [table (atom nil)
         selected-table (e/watch table)]
     (<% :div
         (dom/text "Selected table: " selected-table)
         (<% :select.select {:on-change (e/fn [e]
                                          (reset! table (-> e .-target .-value)))}
             (<% :option "Select table")
             (e/for [[label _] tables]
               (<% :option (dom/text label))))

         (when selected-table
           (TableContents. selected-table))))))
