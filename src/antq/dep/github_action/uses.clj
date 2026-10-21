(ns ^:no-doc antq.dep.github-action.uses
  (:require
   [antq.constant.github-action :as const.gh-action]
   [antq.record :as r]
   [antq.util.ver :as u.ver]
   [clojure.string :as str])
  (:import
   java.util.regex.Pattern))

(defn- sha-1?
  [s]
  (some? (and (re-seq #"^[a-fA-F0-9]+$" s)
              (#{40 7} (count s)))))

(defn name->url
  "The repository URL of the action `name`, e.g.
  `https://github.com/actions/cache.git` for `actions/cache/restore`."
  [^String name]
  (if (= 0 (.indexOf name "https://"))
    name
    (str "https://github.com/" (str/join "/" (take 2 (str/split name #"/"))) ".git")))

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

(defn- version?
  [s]
  (-> s
      (u.ver/normalize-version)
      (u.ver/remove-qualifiers)
      (u.ver/sem-ver?)))

(defn- version-comment
  [content name sha]
  (let [pinned-line (re-pattern (str (Pattern/quote (str name "@" sha))
                                     "\\s+#\\s*(\\S+)"))
        [_ comment-word] (re-find pinned-line content)]
    (when (and comment-word (version? comment-word))
      comment-word)))

(defn detect
  [form]
  (when (and (vector? form)
             (= :uses (first form)))
    (some-> (second form)
            (parse-dep-value)
            (r/map->Dependency)
            (vector))))

(defn with-version-comment
  "Treat an action pinned to a commit SHA as pinned to the release its
  trailing comment names, e.g. `actions/checkout@<sha> # v4.1.0`."
  [dep content]
  (if-let [version (and (= :git-sha (:type dep))
                        (version-comment content (:name dep) (:version dep)))]
    (assoc dep
           :type :git-tag-and-sha
           :version version
           :extra (assoc (:extra dep) :sha (:version dep)))
    dep))

(defn pinnable?
  "Whether `dep` is an action referenced by a release tag, e.g.
  `actions/cache@v5`, that can be pinned to the commit of that tag."
  [dep]
  (and (= :github-tag (:type dep))
       (= "uses" (get-in dep [:extra const.gh-action/type-key]))
       (version? (:version dep))))
