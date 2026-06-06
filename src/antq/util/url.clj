(ns ^:no-doc antq.util.url
  (:require
   [clojure.string :as str]))

(defn ensure-tail-slash
  [s]
  (cond-> s
    (not (str/ends-with? s "/")) (str "/")))

(defn ensure-git-https-url
  [url]
  (if-not (str/starts-with? url "git@")
    (-> url
        (str/replace #"\.git$" "")
        (ensure-tail-slash))
    (let [[_ s] (str/split url #"@" 2)
          [domain s] (str/split s #":" 2)
          ;; Malformed SCM URLs may use a slash instead of a colon between
          ;; the host and path (e.g. git@github.com/foo/bar.git), in which
          ;; case there is no second segment to split on.
          [domain s] (if (some? s)
                       [domain s]
                       (str/split domain #"/" 2))
          path (str/replace (or s "") #"\.git$" "")]
      (-> (format "https://%s/%s" domain path)
          (ensure-tail-slash)))))

(defn ensure-https
  [url]
  (cond-> url
    (str/starts-with? url "http://") (str/replace #"^http://" "https://")))
