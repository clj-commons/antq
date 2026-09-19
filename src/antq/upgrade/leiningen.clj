(ns ^:no-doc antq.upgrade.leiningen
  (:require
   [antq.constant :as const]
   [antq.upgrade :as upgrade]
   [antq.util.dep :as u.dep]
   [antq.util.zip :as u.zip]
   [rewrite-clj.zip :as z]))

(defn- in-dependencies?
  [loc]
  (loop [loc (-> loc z/up z/up)]
    (case (z/tag loc)
      :meta (recur (z/up loc))
      :vector (contains? #{:dependencies :plugins} (-> loc z/left z/sexpr))
      false)))

(defn- ignoring-meta?
  [loc]
  (if-let [loc (some-> loc z/up z/up)]
    (and (= :meta (z/tag loc))
         (= const/deps-exclude-key
            (some-> loc z/down z/sexpr)))
    false))

(defn- target-dependencies?
  [loc]
  (and (in-dependencies? loc)
       (not (ignoring-meta? loc))))

(defn upgrade-dep
  [loc version-checked-dep]
  (let [name-set (u.dep/name-candidates (:name version-checked-dep))]
    (loop [loc loc]
      (if-let [loc (z/find-value loc z/next name-set)]
        (recur (if (target-dependencies? loc)
                 (-> loc
                     (z/right)
                     (z/replace (:latest-version version-checked-dep)))
                 (z/next loc)))
        (u.zip/move-to-root loc)))))

(defmethod upgrade/upgrader :leiningen
  [version-checked-dep]
  (-> (z/of-file (:file version-checked-dep))
      (upgrade-dep version-checked-dep)
      (z/root-string)))
