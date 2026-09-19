(ns ^:no-doc antq.dep.babashka
  (:require
   [antq.constant.project-file :as const.project-file]
   [antq.dep.clojure :as dep.clj]
   [antq.record :as r]
   [antq.util.dep :as u.dep]
   [clojure.java.io :as io]))

(defn load-deps
  {:malli/schema [:function
                  [:=> :cat [:maybe r/?dependencies]]
                  [:=> [:cat 'string?] [:maybe r/?dependencies]]]}
  ([] (load-deps "."))
  ([dir]
   (let [file (io/file dir const.project-file/babashka)]
     (when (.exists file)
       (dep.clj/extract-deps (u.dep/relative-path file)
                             (slurp file))))))
