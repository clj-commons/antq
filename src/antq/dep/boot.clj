(ns ^:no-doc antq.dep.boot
  (:require
   [antq.constant :as const]
   [antq.constant.project-file :as const.project-file]
   [antq.record :as r]
   [antq.util.dep :as u.dep]
   [clojure.java.io :as io]
   [clojure.walk :as walk]))

(defn- exclude?
  [v]
  (-> (meta v)
      (get const/deps-exclude-key)
      (true?)))

(defn- exclude-version-range
  [v]
  (-> (meta v)
      (get const/deps-exclude-key)
      (u.dep/ensure-version-list)))

(defn extract-deps
  {:malli/schema [:=>
                  [:cat 'string? 'string?]
                  r/?dependencies]}
  [file-path build-boot-content-str]
  (let [dep-form? (atom false)
        repos-form? (atom false)
        deps (atom [])
        repos (atom [])]
    (walk/prewalk (fn [form]
                    (cond
                      (keyword? form)
                      (do (reset! dep-form? (= :dependencies form))
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
                           (string? (first form)))
                      (swap! repos concat form))
                    form)
                  (read-string (str "(list " build-boot-content-str " )")))
    (let [repositories (apply hash-map @repos)]
      (for [[dep-name version :as dep] @deps
            :when (and (string? version) (seq version))]
        (r/map->Dependency {:project :boot
                            :type :java
                            :file file-path
                            :name  (if (qualified-symbol? dep-name)
                                     (str dep-name)
                                     (str dep-name "/" dep-name))
                            :version version
                            :repositories repositories
                            :exclude-versions (seq (exclude-version-range dep))})))))

(defn load-deps
  {:malli/schema [:function
                  [:=> :cat [:maybe r/?dependencies]]
                  [:=> [:cat 'string?] [:maybe r/?dependencies]]]}
  ([] (load-deps "."))
  ([dir]
   (let [file (io/file dir const.project-file/boot)]
     (when (.exists file)
       (extract-deps (u.dep/relative-path file)
                     (slurp file))))))
