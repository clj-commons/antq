(ns ^:no-doc antq.util.aether
  (:require
   [antq.log :as log]
   [antq.util.maven :as u.mvn]
   [clojure.tools.deps.util.maven :as deps.util.maven]
   [clojure.tools.deps.util.session :as deps.util.session])
  (:import
   eu.maveniverse.maven.mima.context.Context
   (org.apache.maven.model
    Model
    Repository)
   (org.apache.maven.settings
    Server
    Settings)
   (org.eclipse.aether
    DefaultRepositorySystemSession
    RepositorySystem)
   (org.eclipse.aether.artifact
    Artifact)
   (org.eclipse.aether.resolution
    VersionRangeRequest)
   (org.eclipse.aether.transfer
    TransferEvent
    TransferListener)))

(defn- new-repository-server
  ^Server
  [{:keys [id username password]}]
  (doto (Server.)
    (.setId id)
    (.setUsername (u.mvn/ensure-username-or-password username))
    (.setPassword (u.mvn/ensure-username-or-password password))))

(defn get-maven-settings
  ^Settings
  [opts]
  (let [settings ^Settings (deps.util.maven/get-settings)
        server-ids (set (map #(.getId ^Server %) (.getServers settings)))]
    (doseq [[id {:keys [username password]}] (u.mvn/credentials (:repositories opts))
            :when (not (contains? server-ids id))]
      (.addServer settings
                  (new-repository-server {:id id :username username :password password})))
    settings))

(def ^TransferListener custom-transfer-listener
  "Logs corrupted downloads."
  (reify TransferListener
    (transferStarted [_ _event])
    (transferCorrupted [_ event]
      (log/warning (str "Download corrupted:" (.. ^TransferEvent event getException getMessage))))
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
        _ (.setTransferListener session custom-transfer-listener)
        artifact (deps.util.maven/coord->artifact lib {:mvn/version version})
        remote-repos (deps.util.maven/remote-repos system session (:repositories opts))]
    {:system system
     :session session
     :artifact artifact
     :remote-repos remote-repos}))

(defn get-versions
  [name opts]
  (let [{:keys [^RepositorySystem system
                ^DefaultRepositorySystemSession  session
                ^Artifact artifact
                remote-repos]} (repository-system name "[0,)" opts)
        req (doto (VersionRangeRequest.)
              (.setArtifact artifact)
              (.setRepositories remote-repos))]
    (->> (.resolveVersionRange system session req)
         (.getVersions))))

(defn model-repositories
  [^Model model]
  (reduce (fn [accm ^Repository repo]
            (assoc accm (.getId repo) {:url (.getUrl repo)}))
          {} (.getRepositories model)))
