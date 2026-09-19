(ns ^:no-doc antq.upgrade.clojure.tool
  (:require
   [antq.upgrade :as upgrade]
   [antq.util.git :as u.git]
   [antq.util.zip :as u.zip]
   [rewrite-clj.zip :as z]))

(defn- replace-git-tag
  [loc version-checked-dep]
  (-> (z/find-value loc z/next :git/tag)
      (z/right)
      (z/replace (:latest-version version-checked-dep))
      (u.zip/move-to-root)))

(defn- replace-git-sha
  [loc version-checked-dep]
  (let [{:keys [latest-version extra]} version-checked-dep
        ;; NOTE: the result of `git ls-remote` should be cached by `memoize`
        new-sha (-> (u.git/tag-sha-by-ls-remote (:url extra) latest-version)
                    (subs 0 (count (:sha extra))))]
    (-> (z/find-value loc z/next :git/sha)
        (z/right)
        (z/replace new-sha)
        (u.zip/move-to-root))))

(defmethod upgrade/upgrader :clojure-tool
  [version-checked-dep]
  (-> (z/of-file (:file version-checked-dep))
      (replace-git-tag version-checked-dep)
      (replace-git-sha version-checked-dep)
      (z/root-string)))
