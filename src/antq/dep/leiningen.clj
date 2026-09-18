(ns ^:no-doc antq.dep.leiningen
  (:require
   [antq.constant :as const]
   [antq.constant.project-file :as const.project-file]
   [antq.record :as r]
   [antq.util.dep :as u.dep]
   [antq.util.leiningen :as u.lein]
   [clojure.java.io :as io]
   [clojure.walk :as walk]))

(defn normalize-repositories
  [repos]
  (reduce (fn [acc [k v]] (assoc acc k (if (map? v) v {:url v}))) {} repos))

(defn normalize-name
  [dep-name]
  (if (qualified-symbol? dep-name)
    (str dep-name)
    (str dep-name "/" dep-name)))

(defn acceptable-version?
  [version]
  (and (string? version) (seq version)))

(defn- exclude?
  [v]
  (-> (meta v)
      (get const/deps-exclude-key)
      (true?)))

(defn exclude-version-range
  [v]
  (-> (meta v)
      (get const/deps-exclude-key)
      (u.dep/ensure-version-list)))

(defn- user-deps-repository
  []
  (let [file (io/file (u.lein/lein-home) "profiles.clj")]
    (when (.exists file)
      (-> file slurp read-string
          (get-in [:user :repositories])))))

(defn extract-deps
  {:malli/schema [:=>
                  [:cat 'string? 'string?]
                  [r/?dependencies]]}
  [file-path project-clj-content-str]
  (let [dep-form? (atom false)
        repos-form? (atom false)
        deps (atom [])
        repos (atom [])
        cross-project-repositories (user-deps-repository)]
    (walk/prewalk (fn [form]
                    (cond
                      (keyword? form)
                      (do (reset! dep-form? (#{:dependencies :managed-dependencies :plugins} form))
                          (reset! repos-form? (= :repositories form)))

                      (and @dep-form?
                           (vector? form)
                           (vector? (first form)))
                      (->> form
                           (seq)
                           (remove exclude?)
                           (swap! deps concat))

                      (and @repos-form?
                           (vector? form)
                           (vector? (first form)))
                      (swap! repos concat form))
                    form)
                  (read-string (str "(list " project-clj-content-str " )")))
    (let [repositories (normalize-repositories @repos)]
      (for [[dep-name version :as dep] @deps
            :when (acceptable-version? version)]
        (r/map->Dependency {:project :leiningen
                            :type :java
                            :file file-path
                            :name (normalize-name dep-name)
                            :version version
                            :repositories (merge repositories
                                                 (into {} cross-project-repositories))
                            :exclude-versions (seq (exclude-version-range dep))})))))

(defn load-deps
  {:malli/schema [:function
                  [:=> :cat [:maybe [r/?dependencies]]]
                  [:=> [:cat 'string?] [:maybe r/?dependencies]]]}
  ([] (load-deps "."))
  ([dir]
   (let [file (io/file dir const.project-file/leiningen)]
     (when (.exists file)
       (extract-deps (u.dep/relative-path file)
                     (slurp file))))))
