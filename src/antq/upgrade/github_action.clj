(ns ^:no-doc antq.upgrade.github-action
  (:require
   [antq.constant.github-action :as const.gh-action]
   [antq.dep.github-action :as dep.gh-action]
   [antq.log :as log]
   [antq.upgrade :as upgrade]
   [clojure.string :as str]
   [rewrite-indented.zip :as ri.zip]))

(defn- update-action-version
  [new-version]
  (fn [using-line]
    (str/replace using-line #"@[^\s]+" (str "@" new-version))))

(defn- update-value
  [new-value]
  (fn [line]
    (str/replace line #"([^:]+\s*:\s*['\"]?)[^\s'\"]+(['\"]?)"
                 (str "$1" new-value "$2"))))

(defn- action?
  [action-name & [version]]
  (let [version' (some-> version
                         (str/replace "." "\\."))
        re (re-pattern (if version'
                         (str "uses\\s*:\\s*" action-name "@" version')
                         (str "uses\\s*:\\s*" action-name)))]
    #(some? (re-seq re %))))

(defmulti upgrade-dep
  (fn [_loc version-checked-dep]
    (dep.gh-action/get-type version-checked-dep)))

(defmethod upgrade-dep :default
  [_ version-checked-dep]
  (log/error (format "%s: Not supported."
                     (dep.gh-action/get-type version-checked-dep))))

(defmethod upgrade-dep "uses"
  [loc version-checked-dep]
  (loop [loc loc]
    (if-let [loc (ri.zip/find-next-string loc (action? (:name version-checked-dep)
                                                       (:version version-checked-dep)))]
      (recur (cond-> loc
               (some? (ri.zip/find-ancestor-string loc #(= "steps:" %)))
               (ri.zip/update (update-action-version (:latest-version version-checked-dep)))

               :always
               (ri.zip/next)))
      (ri.zip/move-to-root loc))))

(defmethod upgrade-dep "DeLaGuardo/setup-clojure"
  [loc version-checked-dep]
  (let [target-re (condp = (:name version-checked-dep)
                    const.gh-action/setup-clojure-name #"cli\s*:"
                    const.gh-action/setup-leiningen-name #"lein\s*:"
                    const.gh-action/setup-boot-name #"boot\s*:"
                    const.gh-action/setup-babashka-name #"bb\s*:"
                    const.gh-action/setup-clj-kondo-name #"clj-kondo\s*:"
                    const.gh-action/setup-cljfmt-name #"cljfmt\s*:"
                    const.gh-action/setup-cljstyle-name #"cljstyle\s*:"
                    const.gh-action/setup-zprint-name #"zprint\s*:"
                    nil)]
    (if-not target-re
      (log/error (format "%s: Unexpected name for setup-clojure"
                         (:name version-checked-dep)))
      (loop [loc loc]
        (if-let [loc (ri.zip/find-next-string loc #(re-seq target-re %))]
          (recur (cond-> loc
                   (some? (ri.zip/find-previous-string loc (action? "DeLaGuardo/setup-clojure")))
                   (ri.zip/update (update-value (:latest-version version-checked-dep)))

                   :always
                   (ri.zip/next)))
          (ri.zip/move-to-root loc))))))

(defmethod upgrade-dep "DeLaGuardo/setup-clj-kondo"
  [loc version-checked-dep]
  (loop [loc loc]
    (if-let [loc (ri.zip/find-next-string loc #(re-seq #"version\s*:" %))]
      (recur (cond-> loc
               (some? (ri.zip/find-previous-string loc (action? "DeLaGuardo/setup-clj-kondo")))
               (ri.zip/update (update-value (:latest-version version-checked-dep)))

               :always
               (ri.zip/next)))
      (ri.zip/move-to-root loc))))

(defmethod upgrade-dep "DeLaGuardo/setup-graalvm"
  [loc version-checked-dep]
  (loop [loc loc]
    (if-let [loc (or (ri.zip/find-next-string loc #(re-seq #"graalvm\s*:" %))
                     (ri.zip/find-next-string loc #(re-seq #"graalvm-version\s*:" %)))]
      (recur (cond-> loc
               (some? (ri.zip/find-previous-string loc (action? "DeLaGuardo/setup-graalvm")))
               (ri.zip/update (update-value (:latest-version version-checked-dep)))

               :always
               (ri.zip/next)))
      (ri.zip/move-to-root loc))))

(defmethod upgrade-dep "0918nobita/setup-cljstyle"
  [loc version-checked-dep]
  (loop [loc loc]
    (if-let [loc (ri.zip/find-next-string loc #(re-seq #"cljstyle-version\s*:" %))]
      (recur (cond-> loc
               (some? (ri.zip/find-previous-string loc (action? "0918nobita/setup-cljstyle")))
               (ri.zip/update (update-value (:latest-version version-checked-dep)))

               :always
               (ri.zip/next)))
      (ri.zip/move-to-root loc))))

(defmethod upgrade/upgrader :github-action
  [version-checked-dep]
  (some-> (:file version-checked-dep)
          (ri.zip/of-file)
          (upgrade-dep version-checked-dep)
          (ri.zip/root-string)))
