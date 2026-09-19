(ns ^:no-doc antq.ver.git-sha
  (:require
   [antq.util.exception :as u.ex]
   [antq.util.git :as u.git]
   [antq.ver :as ver]))

(defmethod ver/get-sorted-versions :git-sha
  [dep _options]
  (try
    (or (some-> (get-in dep [:extra :url])
                (u.git/head-sha-by-ls-remote)
                (vector))
        [])
    (catch Exception ex
      (if (u.ex/ex-timeout? ex)
        [ex]
        (throw ex)))))

(defmethod ver/latest? :git-sha
  [dep]
  (let [current (some-> dep :version)
        latest (some-> dep :latest-version)]
    (if (and (string? current)
             (string? latest))
      (= (subs latest 0 (count current))
         current)
      false)))
