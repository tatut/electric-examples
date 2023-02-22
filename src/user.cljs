(ns user ^:dev/always ; recompile (macroexpand) electric-main when any cljs src changes
    (:require
     app.todo-list
     app.mappy
     app.read-file
     app.ankka
     hyperfiddle.electric
     [hyperfiddle.electric-dom2 :as dom]
     [hyperfiddle.electric :as e]
     [hyperfiddle.api :as hf]
     [hyperfiddle.history :as router]
     contrib.ednish
     [clojure.string :as str]))


(e/defn Link [route label active?]
  (dom/a (dom/props {:class (str "tab" (when active? " tab-active"))
                     ::dom/href (router/encode route)})
         (dom/on "click"
                 (e/fn [e]
                   (.preventDefault e)
                   (router/navigate! router/!history route)))
         (dom/text label)))

(e/defn Demo []
  (router/router
   (router/HTML5-History.)
   (dom/div (dom/props {:class "tabs"})
            (let [active-page (first router/route)]
              (e/for [[demo label] [[['todo] "Todo list"]
                                    [['openlayers] "OpenLayers"]
                                    [['file] "File viewer"]
                                    [['ankka] "DuckDB"]]]
                (Link. demo label (= active-page (first demo))))))

   #_(let [_ (js/console.log "ROUTE: " router/route)]
     (dom/text "olet " router/route " sivulla ")
     (app.todo-list/Debug.))
   (let [[page & args] router/route]
     (case page
       'todo (app.todo-list/Todo-list.)
       'openlayers (app.mappy/Mappy.)
       'file (app.read-file/FileViewer. args)
       'ankka (app.ankka/Listing.))

     (dom/div "No demo selected"))))

(def electric-main
  (hyperfiddle.electric/boot ; Electric macroexpansion - Clojure to signals compiler
   (binding [hyperfiddle.electric-dom2/node js/document.body
             router/encode contrib.ednish/encode-uri
             router/decode #(contrib.ednish/decode-path % hf/read-edn-str)]
     (Demo.)
     #_(app.todo-list/Todo-list.)
     #_(app.mappy/Mappy.))))

(defonce reactor nil)

(defn ^:dev/after-load ^:export start! []
  (assert (nil? reactor) "reactor already running")
  (set! reactor (electric-main
                  #(js/console.log "Reactor success:" %)
                  #(js/console.error "Reactor failure:" %))))

(defn ^:dev/before-load stop! []
  (when reactor (reactor)) ; teardown
  (set! reactor nil))
