(ns antq.api
  (:require
   [antq.core :as core]
   [antq.dep.clojure :as dep.clojure]
   [antq.report :as report]
   [antq.upgrade :as upgrade]
   [antq.util.file :as u.file]))

(defn outdated-deps
  ;; since records can't have docstrings we'll describe the Dependency record for our users here
  ;; we'll consider the fact that it is a record an implementation detail and describe as map
  "Returns a sequence of maps describing outdated dependencies: 
  - `:type` _keyword_ dependency type: `:git-sha`, `:git-tag-and-sha`, `:github-tag` `:java`, or `:circle-ci-orb`
  - `:file` _string_ file path for project configuration file
  - `:name` _string_ dependency name, .e.g., `\"org.clojure/clojure\"`, `\"medley/medley\"`
  - `:version` _string_ current version 
  - `:latest-version` _string_ latest version (can be `nil`)
  - `:repositories` _map_ additional maven repositories (can be `nil`), .e.g.,
     `{\"nexus-snapshots\" {:url \"http://localhost:8081/repository/maven-snapshots/\"}}`
  - `:project` _keyword_ project type: `:boot`, `:clojure`, `:clojure-tool`, `:github-action`, `:gradle`, `:leiningen`, `:pom`, `:shadow-cljs`, or `:circle-ci`
  - `changes-url` _string_ url that describes changes for `:latest-version` (can be `nil`) 
  - `latest-name` _string_ See https://github.com/clojars/clojars-web/wiki/Verified-Group-Names (can be `nil`)
  - `only-newest-version?` _boolean_ keep only newest version in same file
  - `exclude-versions` _sequence of strings_ ignore version that match 
  - `parent` parent dependency name

  Arguments: 
  - `deps-map` _required_ - A map of the same form as `:deps` in `deps.edn`, e.g.,
    `{org.clojure/clojure {:mvn/version \"1.11.1\"}}`

  - `options` _optional_ - A [CLI options](/README.adoc#options) map which can also include:
     - `:repositories` A map of the same form as `:mvn/repos` in `deps.edn`, e.g.,
        `{\"clojars\" {:url \"https://clojars.org/repo\"}}`"
  ([deps-map]
   (outdated-deps deps-map {}))
  ([deps-map {:as options :keys [repositories file-path] :or {file-path ""}}]
   (let [deps-edn (cond-> {:deps deps-map}
                    repositories (assoc :mvn/repos repositories))
         antq-deps (dep.clojure/extract-deps file-path (pr-str deps-edn))
         antq-options (-> options
                          (dissoc :repositories)
                          (assoc :reporter report/no-output-reporter))]
     (core/antq antq-options antq-deps))))

(defn upgrade-deps!
  "Upgrade version strings in specified files.

  Returns a map as follows:
  `{true [upgraded-deps] false [non-upgraded deps]}`

  Arguments: 
  - `file-dep-pairs` _required_ - A vector of maps, e.g.,
     `{:file \"File path to upgrade\" :dependency outdated-dependency-map}`
  - `options` _optional_ - A [CLI options](/README.adoc#options) map."
  ([file-dep-pairs]
   (upgrade-deps! file-dep-pairs {}))
  ([file-dep-pairs options]
   (let [deps (map #(assoc (:dependency %)
                           :file (:file %)
                           :project (u.file/detect-project (:file %)))
                   file-dep-pairs)
         options (assoc options
                        :force true
                        :reporter report/no-output-reporter)]
     (upgrade/upgrade! deps options))))

(comment
  (outdated-deps '{org.clojure/clojure {:mvn/version "1.8.0"}}
                 {:no-changes true})

  (upgrade-deps! [{:file "/tmp/deps.edn"
                   :dependency {:project :clojure
                                :type :java
                                :name "org.clojure/clojure"
                                :version "1.8.0"
                                :latest-version "1.11.1"}}]))
