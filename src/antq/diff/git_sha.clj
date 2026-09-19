(ns ^:no-doc antq.diff.git-sha
  (:require
   [antq.diff :as diff]
   [antq.log :as log]
   [antq.util.url :as u.url]
   [clojure.string :as str]))

(defmethod diff/get-diff-url :git-sha
  [dep]
  (when-let [url (get-in dep [:extra :url])]
    (cond
      (str/starts-with? url "https://github.com/")
      (format "%scompare/%s...%s"
              (-> url
                  (u.url/ensure-git-https-url)
                  (u.url/ensure-tail-slash))
              (:version dep)
              (:latest-version dep))

      :else
      (do (log/warning (str "Diff is not supported for " url))
          nil))))
