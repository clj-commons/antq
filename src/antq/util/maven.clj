(ns ^:no-doc antq.util.maven
  (:require
   [antq.constant :as const]
   [antq.log :as log]
   [antq.util.bb :refer [if-bb]]
   [antq.util.env :as u.env]
   [antq.util.leiningen :as u.lein]
   [antq.util.ver :as u.ver]
   [antq.util.xml :as u.xml]
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.string :as str])
  (:import
   (java.net
    Authenticator
    HttpURLConnection
    PasswordAuthentication
    URI)
   java.time.Instant
   java.util.Base64))

(if-bb (require '[babashka.deps.maven :as deps.maven]) nil)

(def default-repos
  {"central" {:url "https://repo1.maven.org/maven2/"}
   "clojars" {:url "https://repo.clojars.org/"}})

(defn normalize-repo-url
  "c.f. https://clojure.org/reference/deps_and_cli#_maven_s3_repos"
  [url]
  (str/replace url #"^s3p://" "s3://"))

(defn normalize-repos
  [repos]
  (reduce-kv
   (fn [acc k v]
     (assoc acc k (if (contains? v :url)
                    (update v :url normalize-repo-url)
                    v)))
   {} repos))

(defn snapshot?
  [s]
  (if s
    (or (str/includes? (str/lower-case s) "snapshot")
        (u.ver/timestamped-snapshot? s))
    false))

(defn ensure-username-or-password
  [x]
  (if (string? x)
    x
    (or (u.lein/env x)
        (str x))))

(defn- get-auth-info
  [repository]
  (let [[id {:keys [url username password creds]}] repository]
    (cond
      (and username password)
      {:id id
       :username username
       :password password}

      (= :gpg creds)
      (let [credential-info (u.lein/get-credential url)]
        {:id id
         :username (:username credential-info)
         :password (:password credential-info)}))))

(defn credentials
  "Returns repository id to :url, :username and :password for the
  repositories with credentials."
  [repositories]
  (into {}
        (keep (fn [[id {:keys [url]} :as repository]]
                (let [{:keys [username password]} (get-auth-info repository)]
                  (when (and username password)
                    [id {:url url
                         :username (ensure-username-or-password username)
                         :password (ensure-username-or-password password)}]))))
        repositories))

(defn read-pom
  "Returns the url and scm url of a POM file as a map."
  [^java.io.File file]
  (let [content (:content (xml/parse-str (slurp file)))
        scm (first (u.xml/get-tags :scm content))]
    {:url (some-> (u.xml/get-value :url content) str/trim not-empty)
     :scm-url (some-> (when scm (u.xml/get-value :url (:content scm))) str/trim not-empty)}))

(defn- get-local-versions*
  [name]
  (let [sep (System/getProperty "file.separator")
        path (-> (str name)
                 (str/replace "/" sep)
                 (str/replace "." sep))
        file (io/file (u.env/getenv "HOME") ".m2" "repository" path "maven-metadata-local.xml")]
    (when (.exists file)
      (try
        (->> (slurp file)
             (xml/parse-str)
             (xml-seq)
             (u.xml/get-tags :version)
             (map (comp first :content)))
        (catch Exception ex
          (log/warning (str "Failed to get local versions for "
                            name
                            " from "
                            (.getAbsolutePath file)
                            " because: "
                            (.getMessage ex)))
          nil)))))

(def get-local-versions
  (memoize get-local-versions*))

(defn pom-url
  "Returns the URL of the POM of `version` in the repository at `repo-url`.
  A classifier in `dep-name` (`lib$natives-linux`) is dropped: all classifiers share one POM."
  [repo-url dep-name version]
  (let [[group-id artifact-id] (-> (str/replace dep-name #"\$.*" "")
                                   (str/split #"/" 2))
        artifact-id (or artifact-id group-id)]
    (str (str/replace repo-url #"/+$" "") "/"
         (str/replace group-id "." "/") "/" artifact-id "/" version "/"
         artifact-id "-" version ".pom")))

(defn last-modified
  "Returns the `Last-Modified` header of `url` as an Instant, or nil if it can't be read."
  [url {:keys [username password]}]
  (try
    (let [conn ^HttpURLConnection (.openConnection (.toURL (URI. url)))
          timeout (int const/maven-timeout-msec)]
      (.setRequestMethod conn "HEAD")
      (.setConnectTimeout conn timeout)
      (.setReadTimeout conn timeout)
      (when (and username password)
        (.setRequestProperty conn "Authorization"
                             (str "Basic " (.encodeToString (Base64/getEncoder)
                                                            (.getBytes (str username ":" password) "UTF-8")))))
      (when (= 200 (.getResponseCode conn))
        (let [ms (.getLastModified conn)]
          (when (pos? ms)
            (Instant/ofEpochMilli ms)))))
    (catch Exception _ nil)))

(defn- release-date*
  [repositories dep-name version]
  (let [creds (credentials repositories)]
    (some (fn [[id {:keys [url]}]]
            (when url
              (last-modified (pom-url url dep-name version) (get creds id))))
          repositories)))

(def release-date
  "Returns the release date of a version as an Instant, or nil.
  It is the `Last-Modified` header of the version's POM in the first repository that has one."
  (memoize release-date*))

(defn authenticator
  ^Authenticator
  [^String username ^String password]
  (proxy [Authenticator] []
    (getPasswordAuthentication []
      (PasswordAuthentication. username (char-array password)))))

(defn- active-proxy
  []
  (if-bb
   (deps.maven/active-proxy)
   (when-let [prxy (some-> ((requiring-resolve 'antq.util.aether/get-maven-settings) {})
                           (.getActiveProxy))]
     {:host (.getHost prxy)
      :port (.getPort prxy)
      :username (.getUsername prxy)
      :password (.getPassword prxy)})))

(defn initialize-proxy-setting!
  []
  (when-let [{:keys [host port username password]} (active-proxy)]
    (System/setProperty "http.proxyHost" host)
    (System/setProperty "http.proxyPort" (str port))
    (System/setProperty "https.proxyHost" host)
    (System/setProperty "https.proxyPort" (str port))
    (when (and username password)
      (System/setProperty "jdk.http.auth.tunneling.disabledSchemes" "")
      (System/setProperty "jdk.http.auth.proxying.disabledSchemes" "")
      (Authenticator/setDefault (authenticator username password)))))
