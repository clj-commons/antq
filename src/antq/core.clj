;; Warn on Clojure 1.7.0 or earlier
(let [{:keys [major minor]} *clojure-version*]
  (when-not (or (and (= major 1) (>= minor 10))
                (> major 1))
    (.println ^java.io.PrintWriter *err* "antq requires Clojure 1.10.0 or later.")
    (System/exit 1)))

(ns ^:no-doc antq.core
  (:gen-class)
  (:require
   [antq.changelog :as changelog]
   [antq.cli]
   [antq.dep.babashka :as dep.bb]
   [antq.dep.boot :as dep.boot]
   [antq.dep.circle-ci :as dep.circle-ci]
   [antq.dep.clojure :as dep.clj]
   [antq.dep.clojure.tool :as dep.clj.tool]
   [antq.dep.github-action :as dep.gh-action]
   [antq.dep.gradle :as dep.gradle]
   [antq.dep.leiningen :as dep.lein]
   [antq.dep.pom :as dep.pom]
   [antq.dep.shadow :as dep.shadow]
   [antq.dep.transitive :as dep.transitive]
   [antq.diff :as diff]
   [antq.diff.git-sha]
   [antq.diff.github-tag]
   [antq.diff.java]
   [antq.log :as log]
   [antq.record :as r]
   [antq.report :as report]
   [antq.report.edn]
   [antq.report.format]
   [antq.report.json]
   [antq.report.table]
   [antq.upgrade :as upgrade]
   [antq.upgrade.boot]
   [antq.upgrade.circle-ci]
   [antq.upgrade.clojure]
   [antq.upgrade.clojure.tool]
   [antq.upgrade.github-action]
   [antq.upgrade.leiningen]
   [antq.upgrade.pom]
   [antq.upgrade.shadow]
   [antq.util.exception :as u.ex]
   [antq.util.file :as u.file]
   [antq.util.maven :as u.maven]
   [antq.util.ver :as u.ver]
   [antq.ver :as ver]
   [antq.ver.circle-ci-orb]
   [antq.ver.git-sha]
   [antq.ver.git-tag-and-sha]
   [antq.ver.github-tag]
   [antq.ver.java]
   [clojure.string :as str]
   [version-clj.core :as version])
  (:import
   clojure.lang.ExceptionInfo))

(def ^:private disallowed-unverified-deps-map
  {"antq/antq" "com.github.liquidz/antq"
   "seancorfield/depstar" "com.github.seancorfield/depstar"
   "seancorfield/next.jdbc" "com.github.seancorfield/next.jdbc"})

(def ^:private only-newest-version-dep-names
  #{"org.clojure/clojure"})

(defn- parse-artifact
  "Retrieve artifact name and version from artifact string"
  [artifact]
  (zipmap [:name :version]
          (str/split (str artifact) #"@" 2)))

(defn forced-artifact-version-map
  "Forced artifacts are coming from focus param and contain specific version targeted with @"
  [options]
  (->> (:focus options)
       (map parse-artifact)
       (filter :version)
       (map (juxt :name :version))
       (into {})))

(defn skip-artifacts?
  [dep options]
  (let [exclude-artifacts (set (:exclude options []))
        focus-artifacts (->> []
                             (:focus options)
                             (map (comp :name parse-artifact))
                             set)]
    (cond
      ;; `focus` is prefer than `exclude`
      (seq focus-artifacts)
      (not (contains? focus-artifacts (:name dep)))

      :else
      (contains? exclude-artifacts (:name dep)))))

(defn remove-skipping-versions
  [versions dep options]
  (let [dep-name (:name dep)
        skip-vers (->> (:exclude options)
                       (map parse-artifact)
                       (filter #(= dep-name (:name %)))
                       (keep :version)
                       (concat (or (:exclude-versions dep) []))
                       (distinct))]
    (remove (fn [target-version]
              (some #(u.ver/in-range? % target-version) skip-vers))
            versions)))

(defn using-release-version?
  [dep]
  (contains? #{"RELEASE" "master" "main" "latest"} (:version dep)))

(defn mark-forced-version
  "If dependency is in focused artifacts, sets `:forced-version` information"
  [dep forced-artifacts]
  (if-let [forced-version (get forced-artifacts (:name dep))]
    (assoc dep :forced-version forced-version)
    dep))

(defn- assoc-versions
  [dep options]
  (let [res (if-let [forced-version (:forced-version dep)]
              (assoc dep :_versions [forced-version])
              (assoc dep :_versions (ver/get-sorted-versions dep options)))]
    (report/run-progress dep options)
    res))

(defn latest
  [arg-map]
  (let [dep-name (case (:type arg-map)
                   :java (let [[group-id artifact-id] (str/split (str (:name arg-map "")) #"/" 2)]
                           (str group-id "/" (or artifact-id group-id)))
                   (str (:name arg-map)))
        dep-type (:type arg-map :java)]
    (-> (r/map->Dependency
         {:type dep-type
          :name dep-name})
        (ver/get-sorted-versions {})
        (first)
        (log/info))))

(defn- assoc-latest-version
  [dep options]
  (let [vers (cond->> (:_versions dep)
               (not (ver/under-development? (:version dep)))
               (drop-while ver/under-development?))
        vers (remove-skipping-versions vers dep options)
        latest-version (first vers)]
    (assoc dep :latest-version latest-version)))

(defn- dissoc-no-longer-used-keys
  [dep]
  (dissoc dep :_versions))

(defn distinct-deps
  [deps]
  (->> deps
       (map #(select-keys % [:type :name :version :repositories :extra :forced-version]))
       (map #(if (ver/snapshot? (:version %))
               %
               (dissoc % :version)))
       distinct))

(defn complete-versions-by
  [dep deps-with-vers]
  (if-let [dep-with-vers (some #(and (= (:type dep) (:type %))
                                     (= (:name dep) (:name %))
                                     %)
                               deps-with-vers)]
    (assoc dep :_versions (:_versions dep-with-vers))
    dep))

(defn outdated-deps
  [deps options]
  (let [forced-artifacts (forced-artifact-version-map options)
        org-deps (cond->> deps
                   (:transitive options)
                   (concat (dep.transitive/resolve-transitive-deps deps))

                   :always
                   (remove #(or (skip-artifacts? % options)
                                (using-release-version? %)))
                   (seq forced-artifacts)
                   (mapv #(mark-forced-version % forced-artifacts)))
        uniq-deps (distinct-deps org-deps)
        _ (report/init-progress uniq-deps options)
        uniq-deps-with-vers (doall (pmap #(assoc-versions % options) uniq-deps))
        _ (report/deinit-progress uniq-deps options)
        assoc-latest-version* #(assoc-latest-version % options)
        version-checked-deps (->> org-deps
                                  (pmap #(complete-versions-by % uniq-deps-with-vers))
                                  (map (comp dissoc-no-longer-used-keys
                                             assoc-latest-version*)))
        parent-dep-names (->> version-checked-deps
                              (remove ver/latest?)
                              (keep :parent)
                              (set))]
    (->> version-checked-deps
         (remove #(and (not (:forced-version %))
                       (ver/latest? %)
                       (not (contains? parent-dep-names (:name %))))))))

(defn assoc-changes-url
  [{:as version-checked-dep :keys [version latest-version]}]
  (if-let [url (try
                 (when (and version latest-version
                            (not (u.ex/ex-timeout? latest-version))
                            (not= version latest-version))
                   (or (changelog/get-changelog-url version-checked-dep)
                       (diff/get-diff-url version-checked-dep)))
                 (catch ExceptionInfo ex
                   (when-not (u.ex/ex-timeout? ex)
                     (throw ex))))]
    (assoc version-checked-dep :changes-url url)
    version-checked-dep))

(defn unverified-deps
  [deps]
  (keep #(when-let [verified-name (and (= :java (:type %))
                                       (get disallowed-unverified-deps-map (:name %)))]
           (assoc %
                  :version (:name %)
                  :latest-version nil
                  :latest-name verified-name))
        deps))

(defn- system-exit
  [n]
  (System/exit n))

(defn exit
  [outdated-deps]
  (system-exit (if (seq outdated-deps) 1 0)))

(defn fetch-deps
  [options]
  (let [skip (set (:skip options))]
    (mapcat #(concat
              (when-not (skip "boot") (dep.boot/load-deps %))
              (when-not (skip "circle-ci") (dep.circle-ci/load-deps %))
              (when-not (skip "clojure-cli") (dep.clj/load-deps %))
              (when-not (skip "github-action") (dep.gh-action/load-deps %))
              (when-not (skip "pom") (dep.pom/load-deps %))
              (when-not (skip "shadow-cljs") (dep.shadow/load-deps %))
              (when-not (skip "leiningen") (dep.lein/load-deps %))
              (when-not (skip "babashka") (dep.bb/load-deps %))
              (when-not (skip "gradle") (dep.gradle/load-deps %))
              (when (:check-clojure-tools options) (dep.clj.tool/load-deps)))
            (distinct (:directory options)))))

(defn mark-only-newest-version-flag
  [deps]
  (map #(cond-> %
          (contains? only-newest-version-dep-names (:name %))
          (assoc :only-newest-version? true))
       deps))

(defn- unmark-only-newest-version-flag
  [deps]
  (map #(dissoc % :only-newest-version?)
       deps))

(defn unify-deps-having-only-newest-version-flag
  "Keep only the newest version in the same file if `:only-newest-version?` flag is marked."
  [deps]
  (let [other-deps (remove :only-newest-version? deps)]
    (->> deps
         (filter :only-newest-version?)
         (group-by :file)
         (map (fn [[_ deps]]
                (->> deps
                     (sort (fn [a b] (version/version-compare (:version b) (:version a))))
                     first)))
         (concat other-deps))))

(defn antq
  [options deps]
  (let [deps (->> deps
                  (mark-only-newest-version-flag)
                  (unify-deps-having-only-newest-version-flag))]
    (cond->> (outdated-deps deps options)
      (and (not (:no-diff options))
           (not (:no-changes options)))
      (map assoc-changes-url)

      :always
      (concat (unverified-deps deps))

      :always
      (unmark-only-newest-version-flag))))

(defn main*
  [{:keys [opts warnings errors help]}]
  (u.maven/initialize-proxy-setting!)
  (let [opts (cond-> (update opts :directory u.file/distinct-directory)
               ;; Force "format" reporter when :error-format is specified
               (some? (:error-format opts)) (assoc :reporter "format"))
        deps (when (not (seq errors))
               (fetch-deps opts))]
    (doseq [w warnings]
      ;; a bit weird to log/info but warnings only show if verbose is enabled
      (log/info (str "WARNING: " (:msg w))))
    (cond
      (seq errors)
      (do (doseq [e errors]
            (log/error (str "ERROR: " (:msg e))))
          (log/info "")
          (log/info help)
          (system-exit 1))

      help
      (do
        (log/info help)
        (system-exit 0))

      (seq deps)
      (let [alog (log/start-async-logger!)
            outdated (antq opts deps)]
        (try
          (report/reporter outdated opts)
          (cond-> outdated
            (:upgrade opts)
            (-> (upgrade/upgrade! opts)
                ;; get non-upgraded deps
                (get false))

            true
            (exit))
          (finally
            (log/stop-async-logger! alog))))

      :else
      (do (log/info "No project file")
          (system-exit 1)))))

(defn -main
  "Entrypoint for clojure -M cli usage"
  [& args]
  (let [opts-res (-> (antq.cli/parse-args args)
                     (update :error-format #(some-> %
                                                    (str/replace #"\\n" "\n")
                                                    (str/replace #"\\t" "\t"))))]

    (binding [log/*verbose* (:verbose (:opts opts-res) false)]
      (main* opts-res))))
