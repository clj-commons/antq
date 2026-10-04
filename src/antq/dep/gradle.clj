(ns ^:no-doc antq.dep.gradle
  (:require
   [antq.constant.project-file :as const.project-file]
   [antq.record :as r]
   [antq.util.dep :as u.dep]
   [antq.util.os :as os]
   [babashka.process :as process]
   [clojure.java.io :as io]
   [clojure.string :as str])
  (:import
   java.io.File))

(def gradle-command "gradle")

(def ^:private init-script
  "Gradle init script, passed with `--init-script`.
  It adds a read-only `antqDependencies` task to each project, which prints the project's
  repositories and declared dependencies as lines for antq to read:
    ANTQ_PROJECT;<project path>;<build file>
    ANTQ_REPO;<project path>;<name>;<url>   (an empty project path: repositories from settings.gradle)
    ANTQ_DEP;<project path>;<build file>;<group>:<name>:<version>
  The lines are collected while Gradle configures the build, so the task also works
  with the configuration cache.
  Kept in the code rather than as a resource, so that it is found in the uberjar too."
  "def settingsRepos = []
settingsEvaluated { settings ->
  settings.dependencyResolutionManagement.repositories.withType(MavenArtifactRepository) { r ->
    settingsRepos << 'ANTQ_REPO;;' + r.name + ';' + r.url
  }
}

gradle.beforeProject { p ->
  p.tasks.register('antqDependencies') { t ->
    def lines = ['ANTQ_PROJECT;' + p.path + ';' + p.buildFile]
    lines.addAll(settingsRepos)
    p.repositories.withType(MavenArtifactRepository).each { r ->
      lines << 'ANTQ_REPO;' + p.path + ';' + r.name + ';' + r.url
    }
    p.configurations.each { c ->
      c.dependencies.withType(ExternalModuleDependency).each { d ->
        // Dependencies without a version (e.g. managed by a platform) are skipped
        if (d.version) {
          lines << 'ANTQ_DEP;' + p.path + ';' + p.buildFile + ';' + d.group + ':' + d.name + ':' + d.version
        }
      }
    }
    t.doLast { lines.each { println it } }
  }
}
")

;; Any of these files makes a directory part of a Gradle build
(def ^:private project-files
  [const.project-file/gradle "build.gradle.kts" "settings.gradle" "settings.gradle.kts"])

(defn- gradle-wrapper-name
  []
  (if (os/windows?) "gradlew.bat" "gradlew"))

(defn- gradle-project-dir?
  [dir]
  (some #(.isFile (io/file dir %)) project-files))

(defn find-gradle-wrapper
  "Returns the path of the Gradle wrapper for the project in `dir`.
  The wrapper usually lives in the root project, so parent directories are searched
  as long as they are part of the Gradle build.
  Throws if the wrapper is not executable."
  [dir]
  (loop [dir (.getAbsoluteFile (io/file dir))]
    (when (and dir (gradle-project-dir? dir))
      (let [wrapper (io/file dir (gradle-wrapper-name))]
        (cond
          (not (.isFile wrapper))
          (recur (.getParentFile dir))

          (not (.canExecute wrapper))
          (throw (ex-info (str "Gradle wrapper is not executable: " (.getPath wrapper))
                          {:wrapper (.getPath wrapper)}))

          :else
          (.getPath wrapper))))))

(defn- gradle
  "Runs the project's Gradle wrapper, or the `gradle` command when there is none."
  [project-dir & args]
  (apply process/shell {:continue true :out :string :err :string}
         (or (find-gradle-wrapper project-dir) gradle-command)
         "--project-dir" project-dir
         args))

(defn- gradle-failure
  "Returns an exception for a failed Gradle `task`, with Gradle's output to help with diagnosis."
  [task {:keys [exit out err]}]
  (let [output (str/trim (str out "\n" err))]
    (ex-info (cond-> (str "Gradle task " task " failed with exit code " exit)
               (seq output) (str ":\n" output))
             {:exit exit})))

(defn- read-gradle-build
  "Runs the `antqDependencies` task of antq's init script for the project in `project-dir`
  and its subprojects. Returns the printed lines, split into their fields."
  [project-dir]
  ;; Gradle needs the init script as a file
  (let [init-script-file (File/createTempFile "antq-init" ".gradle")]
    (try
      (spit init-script-file init-script)
      (let [{:keys [exit out] :as result} (gradle project-dir
                                                  "--init-script" (.getPath init-script-file)
                                                  "--quiet"
                                                  "antqDependencies")]
        (when-not (= 0 exit)
          (throw (gradle-failure "antqDependencies" result)))
        (let [lines (->> (str/split-lines out)
                         (filter #(str/starts-with? % "ANTQ_"))
                         (map #(str/split % #";" 4)))]
          ;; Every project prints an ANTQ_PROJECT line, even without dependencies.
          ;; Without any, the init script did not work, e.g. with a future Gradle version,
          ;; and reporting no dependencies would hide that.
          (when-not (some #(= "ANTQ_PROJECT" (first %)) lines)
            (throw (ex-info (str "Gradle did not print the output of antq's init script."
                                 " This Gradle version may not be supported.")
                            {})))
          lines))
      (finally
        (.delete init-script-file)))))

(defn- repositories-by-project
  "Returns a function that returns the repositories of a project, by its project path.
  Repositories from settings.gradle (an empty project path) apply to every project."
  [lines]
  (let [repos (->> lines
                   (filter #(= "ANTQ_REPO" (first %)))
                   (reduce (fn [accm [_ project-path repo-name url]]
                             (assoc-in accm [project-path repo-name] {:url url}))
                           {}))]
    (fn [project-path]
      (not-empty (merge (get repos "") (get repos project-path))))))

(defn- build-file-path
  "Returns the path of a (sub)project's `build-file`, relative in the same way as `project-file`."
  [project-file project-dir build-file]
  (let [relative (.relativize (.toPath (.getCanonicalFile (io/file project-dir)))
                              (.toPath (.getCanonicalFile (io/file build-file))))]
    (u.dep/relative-path (io/file (.getParentFile (io/file project-file)) (str relative)))))

(defn- convert-gradle-dependency
  "e.g. dep-str: 'org.clojure:clojure:1.10.0'"
  [file-path dep-str]
  (let [[group-id artifact-id version] (str/split dep-str #":" 3)]
    (when (and group-id artifact-id version)
      (r/map->Dependency {:project :gradle
                          :type :java
                          :file file-path
                          :name (str group-id "/" artifact-id)
                          :version version}))))

(defn extract-deps
  {:malli/schema [:=>
                  [:cat 'string? 'string?]
                  [:maybe r/?dependencies]]}
  [relative-file-path absolute-file-path]
  (try
    (let [project-dir (.getParent (io/file absolute-file-path))
          lines (read-gradle-build project-dir)
          repos-of (repositories-by-project lines)]
      (->> lines
           (filter #(= "ANTQ_DEP" (first %)))
           ;; the same dependency can be declared in several configurations
           (distinct)
           (keep (fn [[_ project-path build-file dep-str]]
                   (some-> (convert-gradle-dependency
                            (build-file-path relative-file-path project-dir build-file)
                            dep-str)
                           (assoc :repositories (repos-of project-path)))))))
    (catch Exception ex
      ;; Not chained with `ex`: a failing run reports only the root cause,
      ;; e.g. "error=2, No such file or directory" without the command name.
      (throw (ex-info (str "Failed to read " relative-file-path ": " (.getMessage ex))
                      (assoc (ex-data ex) :file relative-file-path))))))

(defn discover-project
  [dir]
  (->> project-files
       (map #(io/file dir %))
       (filter #(.isFile ^File %))
       (first)))

(defn load-deps
  {:malli/schema [:function
                  [:=> :cat [:maybe r/?dependencies]]
                  [:=> [:cat 'string?] [:maybe r/?dependencies]]]}
  ([] (load-deps "."))
  ([dir]
   (when-let [file (discover-project dir)]
     (extract-deps (u.dep/relative-path file)
                   (.getAbsolutePath file)))))
