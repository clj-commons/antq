(ns ^:no-doc antq.upgrade.github-action
  (:require
   [antq.constant.github-action :as const.gh-action]
   [antq.dep.github-action :as dep.gh-action]
   [antq.dep.github-action.uses :as d.gha.uses]
   [antq.log :as log]
   [antq.upgrade :as upgrade]
   [antq.util.git :as u.git]
   [antq.util.zip :as u.zip]
   [clojure.string :as str]
   [rewrite-indented.zip :as ri.zip]))

(defn- update-action-version
  [new-version]
  (fn [using-line]
    (str/replace using-line #"@[^\s]+" (str "@" new-version))))

(defn- update-version-comment
  [new-version]
  (fn [using-line]
    (str/replace using-line #"(#\s*)\S+" (str "$1" new-version))))

(defn- latest-sha
  [{:keys [latest-version extra]}]
  (-> (u.git/tag-sha-by-ls-remote (:url extra) latest-version)
      (subs 0 (count (:sha extra)))))

(defn- current-ref
  [dep]
  (get-in dep [:extra :sha] (:version dep)))

(defn- update-uses-line
  [dep]
  (if (get-in dep [:extra :sha])
    (comp (update-version-comment (:latest-version dep))
          (update-action-version (latest-sha dep)))
    (update-action-version (:latest-version dep))))

(defn- pin-action-version
  [sha tag]
  (update-action-version (str sha " # " tag)))

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

(defn- update-action-lines
  [loc action-name ref update-line]
  (loop [loc loc]
    (if-let [loc (ri.zip/find-next-string loc (action? action-name ref))]
      (recur (cond-> loc
               (some? (ri.zip/find-ancestor-string loc #(= "steps:" %)))
               (ri.zip/update update-line)

               :always
               (ri.zip/next)))
      (ri.zip/move-to-root loc))))

(defn- release-sha
  [dep]
  (u.git/tag-sha-by-ls-remote (d.gha.uses/name->url (:name dep))
                              (:version dep)))

(defn- pinned-content
  [dep sha]
  (-> (u.zip/of-indented-file (:file dep))
      (update-action-lines (:name dep)
                           (:version dep)
                           (pin-action-version sha (:version dep)))
      (u.zip/root-indented-string)))

(defn- pin-dep!
  [dep]
  (if-let [sha (release-sha dep)]
    (do (spit (:file dep) (pinned-content dep sha))
        (log/info (format "Pinned %s '%s' to '%s' in %s."
                          (:name dep) (:version dep) sha (:file dep))))
    (log/warning (format "%s: tag '%s' not found, so it is not pinned."
                         (:name dep) (:version dep)))))

(defmulti upgrade-dep
  (fn [_loc version-checked-dep]
    (dep.gh-action/get-type version-checked-dep)))

(defmethod upgrade-dep :default
  [_ version-checked-dep]
  (log/error (format "%s: Not supported."
                     (dep.gh-action/get-type version-checked-dep))))

(defmethod upgrade-dep "uses"
  [loc version-checked-dep]
  (update-action-lines loc
                       (:name version-checked-dep)
                       (current-ref version-checked-dep)
                       (update-uses-line version-checked-dep)))

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
          (u.zip/of-indented-file)
          (upgrade-dep version-checked-dep)
          (u.zip/root-indented-string)))

(defn pin!
  "Pin each action in `deps` that is referenced by a release tag, e.g.
  `actions/cache@v5`, to the commit of that tag: `actions/cache@<sha> # v5`.
  Rewrites the workflow files in place."
  [deps]
  (run! pin-dep! (into [] (comp (filter d.gha.uses/pinnable?) (distinct)) deps)))
