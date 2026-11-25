(ns ^:no-doc antq.dep.babashka
  (:require
   [antq.constant.project-file :as const.project-file]
   [antq.dep.clojure :as dep.clj]
   [antq.util.file :as u.file]
   [babashka.fs :as fs]))

(defn load-deps
  ([] (load-deps "."))
  ([dir]
   (let [file (fs/file dir const.project-file/babashka)]
     (when (fs/exists? file)
       (dep.clj/extract-deps (u.file/data-friendly-path file)
                             (fs/slurp file))))))
