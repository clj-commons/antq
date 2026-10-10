(ns clean
  (:require
   [babashka.fs :as fs]
   [helper.legacy :as legacy]
   [lread.status-line :as status]))

(defn delete-items
  [items]
  (run! (fn [d]
          (println (format "[%s] %s"
                           (if (fs/exists? d) "d" "-")
                           d))
          (fs/delete-tree d {:force true}))
        items))

(defn task
  [_opts]
  (status/line :head "Deleting build work")
  (println "Deleting (d=deleted -=did not exist)")
  (delete-items ["target"
                 ".cpcache"
                 ".clj-kondo/.cache"
                 ".lsp/.cache"])
  ;; We used to have Gradle artifacts in our source tree, this can be problematic, turf them
  (let [remnants (legacy/build-remnants)]
    (when (seq remnants)
      (println "\nCleaning up legacy build work from source tree")
      (delete-items remnants))))
