(ns app.read-file
  #?(:cljs (:require-macros app.read-file))
  (:require
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   #?(:clj [clojure.java.io :as io])
   [hyperfiddle.history :as router]))

#?(:clj (def root-path (io/file ".")))

#?(:clj (defn files [path]
          (map #(assoc (select-keys (bean %) [:name :canonicalPath :file :directory])
                       :length (when-not (.isDirectory %)
                                 (.length %)))
               (.listFiles (apply io/file root-path path)))))

(e/defn FileViewer [path]
  (router/router
   'file
   (let [!content (atom nil)
         content (e/watch !content)]
     (e/client
      (dom/div
       (dom/text (str "PATH: " (e/server (.getAbsolutePath (apply io/file root-path path)))))
       (dom/table
        (dom/props {::dom/class "table table-zebra m-2 w-1/2"})
        (dom/thead (dom/tr
                    (dom/th (dom/text "Name"))
                    (dom/th (dom/text "Size"))))
        (dom/tbody
         (e/for-by
          :canonicalPath [{:keys [length name directory canonicalPath]} (e/server (files path))]
          (if directory
            (dom/tr
             (dom/td
              (router/link (into ['file] (concat path (list name)))
                           (dom/text (str  name))))
             (dom/td (dom/text "[DIR]")))

            (dom/tr
             (dom/on "click" (e/fn [e]
                               (println "KLIKATTIIN" canonicalPath ", e: " e)
                               (reset! !content
                                       (e/server
                                        (slurp canonicalPath)))))
             (dom/td (dom/text name))
             (dom/td (dom/text length)))))))
       (dom/textarea (dom/props {::dom/style {:height "500px" :width "50vw"}})
                     (dom/text content)))))))
