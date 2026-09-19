(ns ^:no-doc antq.dep.github-action.third-party
  (:require
   [antq.constant.github-action :as const.gh-action]
   [antq.record :as r]
   [antq.util.dep :as u.dep]
   [clojure.string :as str]))

(defmulti detect
  (fn [form]
    (when (and (map? form)
               (:uses form))
      (first (str/split (:uses form) #"@")))))

(defmethod detect :default [_] nil)

(defmethod detect "DeLaGuardo/setup-clojure"
  [form]
  (->> (:with form)
       (keep (fn [[k v]]
               (case k
                 (:tools-deps :cli)
                 {:type :github-tag
                  :name const.gh-action/setup-clojure-name
                  :version v}

                 :lein
                 {:type :github-tag
                  :name const.gh-action/setup-leiningen-name
                  :version v}

                 :boot
                 {:type :github-tag
                  :name const.gh-action/setup-boot-name
                  :version v}

                 :bb
                 {:type :java
                  :name const.gh-action/setup-babashka-name
                  :version v}

                 :clj-kondo
                 {:type :java
                  :name const.gh-action/setup-clj-kondo-name
                  :version v}

                 :cljfmt
                 {:type :github-tag
                  :name const.gh-action/setup-cljfmt-name
                  :version v}

                 :cljstyle
                 {:type :github-tag
                  :name const.gh-action/setup-cljstyle-name
                  :version v}

                 :zprint
                 {:type :java
                  :name const.gh-action/setup-zprint-name
                  :version v}

                 nil)))
       (map #(-> %
                 (assoc-in [:extra const.gh-action/type-key] "DeLaGuardo/setup-clojure")
                 (r/map->Dependency)))))

(defmethod detect "DeLaGuardo/setup-clj-kondo"
  [form]
  (when-let [v (get-in form [:with :version])]
    [(r/map->Dependency
      {:name "clj-kondo/clj-kondo"
       :version v
       :type :java
       :extra {const.gh-action/type-key "DeLaGuardo/setup-clj-kondo"}})]))

(defmethod u.dep/normalize-version-by-name "graalvm/graalvm-ce-builds"
  [dep]
  (update dep :version #(str/replace % #"\.java\d+$" "")))

(defmethod detect "DeLaGuardo/setup-graalvm"
  [form]
  (when-let [v (or
                ;; before v4.0
                (get-in form [:with :graalvm-version])
                ;; v4.0 or later
                (get-in form [:with :graalvm]))]
    [(u.dep/normalize-version-by-name
      (r/map->Dependency
       {:name "graalvm/graalvm-ce-builds"
        :version v
        :type :github-tag
        :extra {const.gh-action/type-key "DeLaGuardo/setup-graalvm"}}))]))

(defmethod detect "0918nobita/setup-cljstyle"
  [form]
  (when-let [v (get-in form [:with :cljstyle-version])]
    [(r/map->Dependency
      {:name "greglook/cljstyle"
       :version v
       :type :github-tag
       :extra {const.gh-action/type-key "0918nobita/setup-cljstyle"}})]))
