(ns ^:no-doc antq.dep.github-action.uses
  (:require
   [antq.constant.github-action :as const.gh-action]
   [antq.record :as r]
   [clojure.string :as str]))

(defn- sha-1?
  [s]
  (some? (and (re-seq #"^[a-fA-F0-9]+$" s)
              (#{40 7} (count s)))))

(defn- name->url
  [^String name]
  (if (= 0 (.indexOf name "https://"))
    name
    (str "https://github.com/" name ".git")))

(defn- extract-type-and-version
  [name version]
  (if (sha-1? version)
    {:type :git-sha
     :version version
     :extra {:url (name->url name)}}
    {:type :github-tag
     :version version}))

(defn- parse-dep-value
  [dep]
  (let [[name version] (str/split dep #"@" 2)]
    (when (seq version)
      (-> {:name name}
          (merge (extract-type-and-version name version))
          (assoc-in [:extra const.gh-action/type-key] "uses")))))

(defn detect
  [form]
  (when (and (vector? form)
             (= :uses (first form)))
    (some-> (second form)
            (parse-dep-value)
            (r/map->Dependency)
            (vector))))
