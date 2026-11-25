(ns ^:no-doc antq.dep.circle-ci
  (:require
   [antq.record :as r]
   [antq.util.file :as u.file]
   [babashka.fs :as fs]
   [clj-yaml.core :as yaml]
   [clojure.string :as str]))

(defn extract-deps
  [file-path content-str]
  (let [parsed (yaml/parse-string content-str)]
    (->> parsed
         :orbs
         vals
         (reduce (fn [acc orb-def]
                   ;; ignore "inline" and "URL" orbs; we only look at "registry" orbs
                   (if (and (string? orb-def)
                            (not (str/starts-with? orb-def "https://")))
                     (let [[orb-name version] (str/split orb-def #"@" 2)]
                       (conj acc (r/map->Dependency {:name orb-name
                                                     :version version
                                                     :type :circle-ci-orb
                                                     :project :circle-ci
                                                     :file file-path})))
                     acc))
                 []))))

(defn load-deps
  {:malli/schema [:function
                  [:=> :cat [:maybe r/?dependencies]]
                  [:=> [:cat 'string?] [:maybe r/?dependencies]]]}
  ([] (load-deps "."))
  ([dir]
   (let [config-file (fs/file dir ".circleci/config.yml")]
     (when (fs/exists? config-file)
       (extract-deps (u.file/data-friendly-path config-file)
                     (fs/slurp config-file))))))
