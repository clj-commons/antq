(ns ^:no-doc antq.changelog
  (:require
   [antq.log :as log]
   [antq.util.dep :as u.dep]
   [antq.util.git :as u.git]
   [antq.util.url :as u.url]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.gitlibs :as gitlibs]))

(def changelog-filenames
  #{"changelog.adoc"
    "changelog.md"
    "changelog.org"
    "changes.md"})

(defn- silent-procure
  [url lib rev]
  (try
    (binding [*err* (java.io.StringWriter.)]
      (gitlibs/procure url lib rev))
    (catch Exception _
      ;; Ignore exception for fallback to diff url
      nil)))

(defn- get-root-file-names
  [url lib rev]
  (when-let [dir (silent-procure url lib rev)]
    (seq (.list (io/file dir)))))

(defmulti get-git-url
  (fn [version-checked-dep]
    (:type version-checked-dep)))

(defmethod get-git-url :default
  [_dep]
  nil)

(defmethod get-git-url :git-sha
  [dep]
  (get-in dep [:extra :url]))

(defmethod get-git-url :git-tag-and-sha
  [dep]
  (get-in dep [:extra :url]))

(defmethod get-git-url :github-tag
  [dep]
  (format "https://github.com/%s"
          (str/join "/" (take 2 (str/split (:name dep) #"/")))))

(defmethod get-git-url :java
  [dep]
  (u.dep/get-scm-url dep))

(defn get-changelog-url
  [{:as version-checked-dep :keys [latest-version]}]
  (when-let [url (get-git-url version-checked-dep)]
    (cond
      (str/starts-with? url "https://github.com/")
      (let [lib (symbol (:name version-checked-dep))
            latest-tag (or (u.git/find-tag url latest-version)
                           ;; If there isn't a tag for latest version
                           "head")
            file-names (get-root-file-names url lib latest-tag)
            changelog (when file-names
                        (some #(and (contains? changelog-filenames (str/lower-case %)) %)
                              file-names))]
        (when changelog
          (str (u.url/ensure-git-https-url url)
               "blob/" latest-tag "/" changelog)))

      :else
      (do (log/warning (str "Changelog is not supported for " url))
          nil))))
