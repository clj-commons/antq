(ns ^:no-doc antq.util.maven
  (:require
   [antq.constant :as const]
   [antq.log :as log]
   [antq.util.async :as u.async]
   [antq.util.env :as u.env]
   [antq.util.leiningen :as u.lein]
   [antq.util.xml :as u.xml]
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.deps.util.maven :as deps.util.maven]
   [clojure.tools.deps.util.session :as deps.util.session])
  (:import
   eu.maveniverse.maven.mima.context.Context
   (java.net
    Authenticator
    PasswordAuthentication)
   (org.apache.maven.model
    Model
    Scm)
   org.apache.maven.model.io.xpp3.MavenXpp3Reader
   (org.apache.maven.settings
    Server
    Settings)
   (org.eclipse.aether
    DefaultRepositorySystemSession
    RepositorySystem)
   (org.eclipse.aether.transfer
    TransferEvent
    TransferListener)))

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
    (str/includes? (str/lower-case s) "snapshot")
    false))

(defn ensure-username-or-password
  [x]
  (if (string? x)
    x
    (or (u.lein/env x)
        (str x))))

(defn- new-repository-server
  ^Server
  [{:keys [id username password]}]
  (doto (Server.)
    (.setId id)
    (.setUsername (ensure-username-or-password username))
    (.setPassword (ensure-username-or-password password))))

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

(defn get-maven-settings
  ^Settings
  [opts]
  (let [settings ^Settings (deps.util.maven/get-settings)
        server-ids (set (map #(.getId ^Server %) (.getServers settings)))]
    ;; NOTE
    ;; In Leiningen, authentication information is defined in project.clj or profiles.clj instead of ~/.m2/settings.xml,
    ;; so if there is authentication information in `:repositories`, apply to `settings`
    (doseq [repo (:repositories opts)]
      (let [{:keys [id username password]} (get-auth-info repo)]
        (when (and username
                   password
                   (not (contains? server-ids id)))
          (.addServer settings
                      (new-repository-server {:id id :username username :password password})))))
    settings))

(def ^TransferListener custom-transfer-listener
  "Copy from clojure.tools.deps.util.maven/console-listener
  But no outputs for `transferStarted`"
  (reify TransferListener
    (transferStarted [_ _event])
    (transferCorrupted [_ event]
      (log/warning (str "Download corrupted:" (.. ^TransferEvent event getException getMessage))))
    ;; This happens when Maven can't find an artifact in a particular repo
    ;; (but still may find it in a different repo), ie this is a common event
    (transferFailed [_ _event])
    (transferInitiated [_ _event])
    (transferProgressed [_ _event])
    (transferSucceeded [_ _event])))

(defn repository-system
  [name version opts]
  (let [lib (cond-> name (string? name) symbol)
        local-repo @deps.util.maven/cached-local-repo
        system ^RepositorySystem (deps.util.session/retrieve :mvn/system #(deps.util.maven/make-system))
        settings ^Settings (get-maven-settings opts)
        context ^Context (deps.util.maven/make-context :local-repo local-repo :settings settings)
        session ^DefaultRepositorySystemSession (deps.util.maven/make-system-session context)
        ;; Overwrite TransferListener not to show "Downloading" messages
        _ (.setTransferListener session custom-transfer-listener)
        ;; c.f. https://stackoverflow.com/questions/35488167/how-can-you-find-the-latest-version-of-a-maven-artifact-from-java-using-aether
        artifact (deps.util.maven/coord->artifact lib {:mvn/version version})
        remote-repos (deps.util.maven/remote-repos system session (:repositories opts))]
    {:system system
     :session session
     :artifact artifact
     :remote-repos remote-repos}))

(defn- read-pom*
  ^Model
  [^String url]
  (with-open [reader (io/reader url)]
    (.read (MavenXpp3Reader.) reader)))

(def ^:private read-pom*-with-timeout
  (u.async/fn-with-timeout
   read-pom*
   const/pom-timeout-msec))

(defn read-pom
  ^Model
  [^String url]
  (when-not (str/includes? url "s3://") ; can't do diff's on s3:// repos, https://github.com/clj-commons/antq/issues/133.
    (loop [i 0]
      (when (< i const/retry-limit)
        (or (try
              (read-pom*-with-timeout url)
              (catch java.net.ConnectException e
                (if (= "Operation timed out" (.getMessage e))
                  (log/warning (str "Fetching pom from " url " failed because it timed out, retrying"))
                  (throw e)))
              (catch java.io.IOException e
                (log/warning (str "Fetching pom from " url " failed because of the following error: " (.getMessage e))))
              (catch org.codehaus.plexus.util.xml.pull.XmlPullParserException e
                ;; e.g. This exception is thrown by reading the following pom.xml
                ;;      https://repo1.maven.org/maven2/jakarta/mail/jakarta.mail-api/2.0.1/jakarta.mail-api-2.0.1.pom
                (log/warning (str "Fetching pom from " url " failed because of the following error: " (.getMessage e)))))
            (recur (inc i)))))))

(defn get-model-url
  ^String
  [^Model model]
  (.getUrl model))

(defn get-model-scm
  ^Scm
  [^Model model]
  (.getScm model))

(defn get-scm-url
  ^String
  [^Scm scm]
  (.getUrl scm))

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

(defn authenticator
  ^Authenticator
  [^String username ^String password]
  (proxy [Authenticator] []
    (getPasswordAuthentication []
      (PasswordAuthentication. username (char-array password)))))

(defn initialize-proxy-setting!
  []
  (when-let [prxy (some-> (get-maven-settings {})
                          (.getActiveProxy))]
    (let [host (.getHost prxy)
          port (.getPort prxy)
          username (.getUsername prxy)
          password (.getPassword prxy)]
      (System/setProperty "http.proxyHost" host)
      (System/setProperty "http.proxyPort" (str port))
      (System/setProperty "https.proxyHost" host)
      (System/setProperty "https.proxyPort" (str port))
      (when (and username password)
        (System/setProperty "jdk.http.auth.tunneling.disabledSchemes" "")
        (System/setProperty "jdk.http.auth.proxying.disabledSchemes" "")
        (Authenticator/setDefault (authenticator username password))))))
