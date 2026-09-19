(ns ^:no-doc antq.upgrade.clojure
  (:require
   [antq.constant :as const]
   [antq.upgrade :as upgrade]
   [antq.util.dep :as u.dep]
   [antq.util.git :as u.git]
   [antq.util.zip :as u.zip]
   [rewrite-clj.zip :as z]))

(defn- in-namespaced-map?
  [loc]
  (boolean
   (some-> loc
           z/up
           z/leftmost
           z/up
           z/namespaced-map?)))

(defn- in-deps?
  [loc]
  (if (in-namespaced-map? loc)
    (->> loc z/up z/leftmost z/up z/left z/sexpr
         (contains? const/clojure-deps-keys))
    (->> loc z/up z/left z/sexpr
         (contains? const/clojure-deps-keys))))

(defn- skip-meta
  [loc]
  (if (= :meta (z/tag loc))
    (-> loc
        (z/down)
        (z/right))
    loc))

(defn- ignoring-meta?
  [loc]
  (and (= :meta (some-> loc z/tag))
       (= const/deps-exclude-key
          (some-> loc z/down z/sexpr))))

(defn- target-deps?
  [loc]
  (and (in-deps? loc)
       (not (ignoring-meta? (z/right loc)))))

(defn- down-considering-namespaced-map
  "cf. https://github.com/clj-commons/rewrite-clj/blob/main/doc/01-user-guide.adoc#impact-of-namespaced-map-context-on-keywords-and-symbols"
  [loc]
  (if (z/namespaced-map? loc)
    (-> loc
        (z/down)
        (z/rightmost)
        (z/down))
    (z/down loc)))

(defmulti replace-versions
  (fn [_loc version-checked-dep]
    (:type version-checked-dep)))

(defmethod replace-versions :default
  [loc version-checked-dep]
  (some-> loc
          (z/find-value z/right :mvn/version)
          (z/right)
          (z/replace (:latest-version version-checked-dep))))

(defmethod replace-versions :git-sha
  [loc version-checked-dep]
  (some-> (or (z/find-value loc z/right :sha)
              (z/find-value loc z/right :git/sha))
          (z/right)
          (z/replace (:latest-version version-checked-dep))))

(defmethod replace-versions :git-tag-and-sha
  [loc version-checked-dep]
  (let [tag-loc (some-> (or (z/find-value loc z/right :tag)
                            (z/find-value loc z/right :git/tag))
                        (z/right)
                        (z/replace (:latest-version version-checked-dep))
                        (z/up)
                        (z/down))
        sha-loc (when tag-loc
                  (or (z/find-value tag-loc z/right :sha)
                      (z/find-value tag-loc z/right :git/sha)))]
    (cond
      (and tag-loc sha-loc)
      (let [{:keys [latest-version extra]} version-checked-dep
            ;; NOTE: the result of `git ls-remote` should be cached by `memoize`
            new-sha (-> (u.git/tag-sha-by-ls-remote (:url extra) latest-version)
                        (subs 0 (count (:sha extra))))]
        (-> sha-loc
            (z/right)
            (z/replace new-sha)))

      tag-loc
      tag-loc

      :else
      loc)))

(defn upgrade-dep
  [loc version-checked-dep]
  (let [name-set (u.dep/name-candidates (:name version-checked-dep))]
    (loop [loc loc]
      (if-let [loc (z/find-value loc z/next name-set)]
        (recur (if (target-deps? loc)
                 (or (some-> loc
                             ;; move to map
                             (z/right)
                             ;; TODO check antq/ignore
                             (skip-meta)
                             (down-considering-namespaced-map)
                             (replace-versions version-checked-dep))
                     (z/next loc))
                 (z/next loc)))
        (u.zip/move-to-root loc)))))

(defmethod upgrade/upgrader :clojure
  [version-checked-dep]
  (-> (z/of-file (:file version-checked-dep))
      (upgrade-dep version-checked-dep)
      (z/root-string)))
