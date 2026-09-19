(ns ^:no-doc antq.download
  (:require
   [antq.util.git :as u.git]
   [clojure.tools.deps :as deps]
   [clojure.tools.deps.util.maven :as mvn]))

(defmulti dep->deps-map :type)

(defmethod dep->deps-map :default [_] nil)

(defmethod dep->deps-map :java
  [dep]
  {(symbol (:name dep)) {:mvn/version (:latest-version dep)}})

(defmethod dep->deps-map :git-sha
  [dep]
  {(symbol (:name dep)) {:git/url (get-in dep [:extra :url])
                         :git/sha (:latest-version dep)}})

(defmethod dep->deps-map :git-tag-and-sha
  [dep]
  (let [git-url (get-in dep [:extra :url])
        latest-version (:latest-version dep)]
    {(symbol (:name dep)) {:git/url git-url
                           :git/tag latest-version
                           :git/sha (u.git/tag-sha-by-ls-remote git-url latest-version)}}))

(defn download!
  [upgraded-deps]
  (let [repos (apply merge mvn/standard-repos (map :repositories upgraded-deps))
        target-deps (keep dep->deps-map upgraded-deps)
        m {:deps (apply merge target-deps)
           :mvn/repos repos}]
    (deps/resolve-deps m nil)))
