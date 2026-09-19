(ns ^:no-doc antq.dep.clojure
  "Clojure CLI"
  (:require
   [antq.constant :as const]
   [antq.constant.project-file :as const.project-file]
   [antq.record :as r]
   [antq.util.dep :as u.dep]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.deps.edn :as deps-edn]
   [clojure.tools.deps.extensions.git :as git]
   [clojure.walk :as walk]))

(declare load-deps)

(defn user-deps-repository
  []
  (let [file (io/file (deps-edn/user-deps-path))]
    (when (.exists file)
      (-> file slurp edn/read-string :mvn/repos))))

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
  (let [file (io/file current-working-directory)
        dir (if (.isDirectory file)
              file
              (.getParentFile file))]
    (if dir
      (-> (u.dep/relative-path dir)
          (io/file relative-path)
          (str))
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
  {:malli/schema [:=>
                  [:cat 'string? 'string? [:* 'any?]]
                  r/?dependencies]}
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
  {:malli/schema [:function
                  [:=> :cat [:maybe r/?dependencies]]
                  [:=> [:cat 'string?] [:maybe r/?dependencies]]
                  [:=> [:cat 'string? 'any?] [:maybe r/?dependencies]]]}
  ([] (load-deps "."))
  ([dir] (load-deps dir (atom #{})))
  ([dir loaded-dir-set]
   (let [dir (u.dep/normalize-path dir)]
     ;; Avoid infinite loop
     (when-not (contains? @loaded-dir-set dir)
       (swap! loaded-dir-set conj dir)
       (let [file (io/file dir const.project-file/clojure-cli)]
         (when (.exists file)
           (extract-deps (u.dep/relative-path file)
                         (slurp file)
                         loaded-dir-set)))))))
