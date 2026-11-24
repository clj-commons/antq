(ns ^:no-doc antq.dep.clojure.tool
  (:require
   [antq.record :as r]
   [babashka.fs :as fs]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.tools.deps.edn :as deps-edn])
  (:import
   java.io.File))

(defn- tools-dir
  ^File []
  (-> (deps-edn/user-deps-path)
      (fs/parent)
      (fs/file "tools")))

(defn extract-deps
  [file-path tool-edn-content-str]
  (let [edn (edn/read-string tool-edn-content-str)
        {:git/keys [sha tag url]} (:coord edn)]
    (when (and sha tag url)
      (r/map->Dependency {:type :git-tag-and-sha
                          :file file-path
                          :name (str (:lib edn))
                          :project :clojure-tool
                          :version tag
                          :extra {:url url :sha sha}}))))

(defn load-deps
  ([]
   (load-deps (tools-dir)))
  ([dir-file]
   (some->> dir-file
            (file-seq)
            (filter (fn [f] (and (fs/regular-file? f) (str/ends-with? (fs/file-name f) ".edn"))))
            (keep (fn [f] (extract-deps (str (fs/absolutize f)) (fs/slurp f)))))))
