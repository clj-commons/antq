(ns ^:no-doc antq.dep.clojure
  "Clojure CLI"
  (:require
   [antq.constant :as const]
   [antq.constant.project-file :as const.project-file]
   [antq.record :as r]
   [antq.util.dep :as u.dep]
   [antq.util.file :as u.file]
   [babashka.fs :as fs]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.tools.deps.edn :as deps-edn]
   [clojure.tools.deps.extensions.git :as git]
   [clojure.walk :as walk]))

(declare load-deps)

(defn user-deps-repository
  []
  (let [file (deps-edn/user-deps-path)]
    (when (fs/exists? file)
      (-> file fs/slurp edn/read-string :mvn/repos))))

(defmulti extract-type-and-version
  (fn [opt]
    (if (map? opt)
      (cond
        (contains? opt :mvn/version)
        :java

        (and (contains? opt :git/url)
             (some #(contains? opt %) [:tag :git/tag])
             (some #(contains? opt %) [:sha :git/sha]))
        :git-tag-and-sha

        (and (contains? opt :git/url)
             (some #(contains? opt %) [:sha :git/sha]))
        :git-sha

        :else
        ::unknown)
      ::unknown)))

(defmethod extract-type-and-version :default
  [_]
  {})

(defmethod extract-type-and-version :java
  [opt]
  {:type :java
   :version (:mvn/version opt)})

(defmethod extract-type-and-version :git-sha
  [opt]
  {:type :git-sha
   :version (:sha opt (:git/sha opt))
   :extra {:url (:git/url opt)}})

(defmethod extract-type-and-version :git-tag-and-sha
  [opt]
  {:type :git-tag-and-sha
   :version (:tag opt (:git/tag opt))
   :extra {:url (:git/url opt)
           :sha (:sha opt (:git/sha opt))}})

(defn- adjust-version-via-deduction
  [dep-name opt]
  (if (and (map? opt)
           (some #{:tag :sha :git/tag :git/sha} (keys opt))
           (not (:git/url opt)))
    (if-let [git-dep (git/auto-git-url dep-name)]
      (assoc opt :git/url git-dep)
      opt)
    opt))

(defn- get-relative-path-by-current-working-directory
  [current-working-directory relative-path]
  (let [file (fs/file current-working-directory)
        dir (if (fs/directory? file)
              file
              (fs/parent file))]
    (if dir
      (str (fs/file dir relative-path))
      relative-path)))

(defn- get-local-root-relative-path
  [current-file-path opt]
  (let [local-root (:local/root opt)]
    (if (str/starts-with? local-root "/")
      local-root
      (get-relative-path-by-current-working-directory
       current-file-path local-root))))

(defn- exclude?
  [dep]
  (-> (second dep)
      (meta)
      (get const/deps-exclude-key)
      (true?)))

(defn- exclude-version-range
  [dep]
  (-> (second dep)
      (meta)
      (get const/deps-exclude-key)
      (u.dep/ensure-version-list)))

(defn extract-deps
  [file-path deps-edn-content-str & [loaded-dir-set]]
  (let [deps (atom [])
        edn (edn/read-string deps-edn-content-str)
        loaded-dir-set (or loaded-dir-set (atom #{}))
        cross-project-repositories (user-deps-repository)]
    (walk/postwalk (fn [form]
                     (when (and (sequential? form)
                                (const/clojure-deps-keys (first form))
                                (map? (second form)))
                       (->> form
                            (second)
                            (seq)
                            (remove exclude?)
                            (swap! deps concat)))
                     form)
                   edn)
    (->> @deps
         (mapcat (fn [[dep-name opt :as dep]]
                   (let [opt (adjust-version-via-deduction dep-name opt)
                         type-and-version (extract-type-and-version opt)]
                     (cond
                       (not (map? opt))
                       [nil]

                       (contains? opt :local/root)
                       (let [path (get-local-root-relative-path file-path opt)]
                         (load-deps path loaded-dir-set))

                       (and (string? (:version type-and-version))
                            (seq (:version type-and-version)))
                       (-> {:project :clojure
                            :file file-path
                            :name  (if (qualified-symbol? dep-name)
                                     (str dep-name)
                                     (str dep-name "/" dep-name))
                            :repositories (merge cross-project-repositories
                                                 (:mvn/repos edn))
                            :exclude-versions (seq (exclude-version-range dep))}
                           (merge type-and-version)
                           (r/map->Dependency)
                           (vector))

                       :else
                       [nil]))))
         (remove nil?))))

(defn load-deps
  ([] (load-deps "."))
  ([dir] (load-deps dir (atom #{})))
  ([dir loaded-dir-set]
   (let [unique-dir (fs/canonicalize dir)] ; can't use real-path because dir might not actually exist
     ;; Avoid infinite loop, load from each dir only once
     (when-not (contains? @loaded-dir-set unique-dir)
       (swap! loaded-dir-set conj unique-dir)
       (let [file (fs/file dir const.project-file/clojure-cli)]
         (when (fs/exists? file)
           (extract-deps (u.file/data-friendly-path file)
                         (fs/slurp file)
                         loaded-dir-set)))))))
