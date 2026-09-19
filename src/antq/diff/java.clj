(ns ^:no-doc antq.diff.java
  (:require
   [antq.diff :as diff]
   [antq.log :as log]
   [antq.util.dep :as u.dep]
   [antq.util.git :as u.git]
   [antq.util.url :as u.url]
   [clojure.string :as str]))

(defmethod diff/get-diff-url :java
  [{:as dep :keys [version latest-version]}]
  (when-let [url (u.dep/get-scm-url dep)]
    (cond
      (str/starts-with? url "https://github.com/")
      (let [current (u.git/find-tag url version)
            latest (or (u.git/find-tag url latest-version)
                       ;; If there isn't a tag for latest version
                       "head")]
        (if current
          (format "%scompare/%s...%s"
                  (u.url/ensure-tail-slash url)
                  current
                  latest)
          (do (log/warning (str "The tag for current version is not found: " url))
              ;; not diff, but URL is useful for finding the differences.
              nil)))

      :else
      (do (log/warning (str "Diff is not supported for " url))
          ;; not diff, but URL is useful for finding the differences.
          nil))))
