(ns ^:no-doc antq.upgrade.circle-ci
  (:require
   [antq.upgrade :as upgrade]
   [clojure.string :as str]
   [rewrite-indented.zip :as ri.zip]))

(defn- update-value
  [new-value]
  (fn [line]
    (str/replace line #"([^@]+\s*@\s*['\"]?)[^\s'\"]+(['\"]?)"
                 (str "$1" new-value "$2"))))

(defn upgrade-dep
  [loc version-checked-dep]
  (loop [loc loc]
    (if-let [loc (ri.zip/find-next-string loc #(re-seq (re-pattern (str "[^:]+\\s*:\\s*" (:name version-checked-dep) "@")) %))]
      (recur (-> (ri.zip/update loc (update-value (:latest-version version-checked-dep)))
                 ri.zip/next))
      (ri.zip/move-to-root loc))))

(defmethod upgrade/upgrader :circle-ci
  [version-checked-dep]
  (some-> (:file version-checked-dep)
          (ri.zip/of-file)
          (upgrade-dep version-checked-dep)
          (ri.zip/root-string)))
