(ns ^:no-doc antq.ver.git-tag-and-sha
  (:require
   [antq.util.exception :as u.ex]
   [antq.util.git :as u.git]
   [antq.util.ver :as u.ver]
   [antq.ver :as ver]
   [version-clj.core :as version]))

(defmethod ver/get-sorted-versions :git-tag-and-sha
  [dep _options]
  (try
    (or (some->> (get-in dep [:extra :url])
                 (u.git/tags-by-ls-remote)
                 (filter (comp u.ver/sem-ver?
                               u.ver/remove-qualifiers
                               u.ver/normalize-version))
                 (sort (fn [& args]
                         (apply version/version-compare
                                (map u.ver/normalize-version args))))
                 (reverse))
        [])
    (catch Exception ex
      (if (u.ex/ex-timeout? ex)
        [ex]
        (throw ex)))))

(defmethod ver/latest? :git-tag-and-sha
  [dep]
  (and (:version dep)
       (:latest-version dep)
       (string? (:version dep))
       (string? (:latest-version dep))
       (<= 0  (version/version-compare
               (u.ver/normalize-version (:version dep))
               (u.ver/normalize-version (:latest-version dep))))))
