(ns ^:no-doc antq.dep.circle-ci
  (:require
   [antq.record :as r]
   [antq.util.dep :as u.dep]
   [clj-yaml.core :as yaml]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn extract-deps
  [file-path content-str]
  (let [parsed (yaml/parse-string content-str)]
    (->> parsed
         :orbs
         vals
         (mapv (fn [orb-s]
                 (let [[orb-name version] (str/split orb-s #"@" 2)]
                   (r/map->Dependency {:name orb-name
                                       :version version
                                       :type :circle-ci-orb
                                       :project :circle-ci
                                       :file file-path})))))))

(defn load-deps
  {:malli/schema [:function
                  [:=> :cat [:maybe r/?dependencies]]
                  [:=> [:cat 'string?] [:maybe r/?dependencies]]]}
  ([] (load-deps "."))
  ([dir]
   (let [config-file (io/file dir ".circleci/config.yml")]
     (when (.exists config-file)
       (extract-deps (u.dep/relative-path config-file)
                     (slurp config-file))))))
