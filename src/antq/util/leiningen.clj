(ns ^:no-doc antq.util.leiningen
  (:require
   [antq.log :as log]
   [antq.util.env :as u.env]
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.string :as str]))

(defn lein-home
  []
  (io/file (System/getProperty "user.home") ".lein"))

(defn- env-name
  "cf. https://github.com/technomancy/leiningen/blob/master/doc/DEPLOY.md#credentials-in-the-environment"
  [kw]
  (cond
    (and (qualified-keyword? kw)
         (= "env" (namespace kw)))
    (str/upper-case (name kw))

    (= :env kw)
    "LEIN_PASSWORD"

    :else
    nil))

(defn- gpg-program
  []
  (or (System/getenv "LEIN_GPG") "gpg"))

(defn- gpg
  [& args]
  (try
    (apply shell/sh (gpg-program) args)
    (catch java.io.IOException e
      {:exit 1 :err (.getMessage e)})))

(defn- credentials-fn*
  "This method references to the code of Leiningen, an open-source project licensed under EPL 1.0.
  cf. https://codeberg.org/leiningen/leiningen/src/tag/2.10.0/leiningen-core/src/leiningen/core/user.clj#L138"
  ([] (let [cred-file (io/file (lein-home) "credentials.clj.gpg")]
        (if (.exists cred-file)
          (credentials-fn* cred-file)
          (log/error (format "Could not find %s" (str cred-file))))))
  ([file]
   (let [{:keys [out err exit]} (gpg "--quiet" "--batch"
                                     "--decrypt" "--" (str file))]
     (if (pos? exit)
       (do
         (log/error (format "Could not decrypt credentials from %s" (str file)))
         (log/error err)
         (log/error "See `lein help gpg` for how to install gpg."))
       (read-string out)))))

(def ^:private credentials-fn (memoize credentials-fn*))

(defn get-credential
  [url]
  (when-let [res (some->> (credentials-fn)
                          (filter (fn [[pattern _]] (re-seq pattern url)))
                          first
                          val)]
    (select-keys res [:username :password])))

(defn env
  [kw]
  (some-> (env-name kw)
          (u.env/getenv)))
