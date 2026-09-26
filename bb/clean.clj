(ns clean
  (:require
   [babashka.fs :as fs]
   [lread.status-line :as status]))

(defn task
  [_opts]
  (status/line :head "Deleting build work")
  (println "Deleting (d=deleted -=did not exist)")
  (run! (fn [d]
          (println (format "[%s] %s"
                           (if (fs/exists? d) "d" "-")
                           d))
          (fs/delete-tree d {:force true}))
        ["target"
         ".cpcache"
         ".clj-kondo/.cache"
         ".lsp/.cache"
         "test/resources/dep/build"
         "test/resources/dep/no_repo_gradle/build"]))
