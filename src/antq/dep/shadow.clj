(ns ^:no-doc antq.dep.shadow
  (:require
   [antq.constant :as const]
   [antq.constant.project-file :as const.project-file]
   [antq.record :as r]
   [antq.util.dep :as u.dep]
   [antq.util.env :as u.env]
   [antq.util.file :as u.file]
   [babashka.fs :as fs]
   [clojure.edn :as edn]
   [clojure.walk :as walk]))

(defn- read-env
  [arg]
  (let [[envname & opts] (cond-> arg
                           (not (sequential? arg)) vector)
        option (when (seq opts)
                 (cond->> opts
                   (not (keyword? (first opts))) (cons :default)
                   true (apply hash-map)))]
    (or (u.env/getenv envname)
        (get option :default))))

(def ^:private readers
  "c.f. https://github.com/thheller/shadow-cljs/blob/2.10.21/src/main/shadow/cljs/devtools/config.clj#L102-L107"
  {:readers
   {'shadow/env read-env
    'env read-env}})

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
  [file-path shadow-cljs-edn-content-str]
  (let [deps (atom [])]
    (walk/postwalk (fn [form]
                     (when (and (sequential? form)
                                (= :dependencies (first form)))
                       (->> form
                            (second)
                            (seq)
                            (remove exclude?)
                            (swap! deps concat)))
                     form)
                   (edn/read-string readers shadow-cljs-edn-content-str))
    (for [[dep-name version :as dep] @deps
          :when (and (string? version) (seq version))]
      (r/map->Dependency {:project :shadow-cljs
                          :type :java
                          :file file-path
                          :name  (str dep-name)
                          :version version
                          :exclude-versions (seq (exclude-version-range dep))}))))

(defn load-deps
  ([] (load-deps "."))
  ([dir]
   (let [file (fs/file dir const.project-file/shadow-cljs)]
     (when (fs/exists? file)
       (extract-deps (u.file/data-friendly-path file)
                     (fs/slurp file))))))
