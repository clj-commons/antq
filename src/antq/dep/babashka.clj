(ns ^:no-doc antq.dep.babashka
  (:require
   [antq.constant.project-file :as const.project-file]
   [antq.dep.clojure :as dep.clj]
   [antq.record :as r]
   [antq.util.file :as u.file]
   [babashka.fs :as fs]))

(defn load-deps
  {:malli/schema [:function
                  [:=> :cat [:maybe r/?dependencies]]
                  [:=> [:cat 'string?] [:maybe r/?dependencies]]]}
  ([] (load-deps "."))
  ([dir]
   (let [file (fs/file dir const.project-file/babashka)]
     (when (fs/exists? file)
       (dep.clj/extract-deps (u.file/data-friendly-path file)
                             (fs/slurp file))))))
