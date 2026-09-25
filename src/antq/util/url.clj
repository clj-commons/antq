(ns ^:no-doc antq.util.url
  (:require
   [clojure.string :as str]))

(defn ensure-tail-slash
  [s]
  (cond-> s
    (not (str/ends-with? s "/")) (str "/")))

(defn ensure-git-https-url
  [url]
  (let [url (str/replace url #"\.git$" "")
        url (if-not (str/starts-with? url "git@")
              url
              ;; convert: git@host:path
              ;; or: malformed git@host/path
              (let [[host path] (str/split (subs url 4) #"[:/]" 2)]
                (str "https://" host "/" path)))]
    (ensure-tail-slash url)))

(defn ensure-https
  [url]
  (cond-> url
    (str/starts-with? url "http://") (str/replace #"^http://" "https://")))
