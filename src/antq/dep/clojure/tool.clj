(ns ^:no-doc antq.dep.clojure.tool
  (:require
   [antq.record :as r]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.deps.edn :as deps-edn])
  (:import
   java.io.File))

(defn- tools-dir
  ^File []
  (-> (deps-edn/user-deps-path)
      (io/file)
      (.getParentFile)
      (io/file "tools")))

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
            (filter (fn [^File f] (and (.isFile f) (str/ends-with? (.getName f) ".edn"))))
            (keep (fn [^File f] (extract-deps (.getAbsolutePath f) (slurp f)))))))
