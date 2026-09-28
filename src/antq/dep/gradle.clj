(ns ^:no-doc antq.dep.gradle
  (:require
   [antq.constant.project-file :as const.project-file]
   [antq.record :as r]
   [antq.util.dep :as u.dep]
   [clojure.java.io :as io]
   [clojure.java.shell :as sh]
   [clojure.string :as str]))

(def gradle-command "gradle")
(def ^:private dep-regexp #"^[^-]\-+\s")

(defn- windows?
  []
  (-> (System/getProperty "os.name")
      (str/lower-case)
      (str/starts-with? "windows")))

(defn- gradle-wrapper-name
  []
  (if (windows?) "gradlew.bat" "gradlew"))

(defn- gradle-project-dir?
  [dir]
  (some #(.isFile (io/file dir %))
        ["build.gradle" "build.gradle.kts" "settings.gradle" "settings.gradle.kts"]))

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
  (apply sh/sh (or (find-gradle-wrapper project-dir) gradle-command)
         "--project-dir" project-dir
         args))

(defn- get-repositories
  [file-path]
  (let [parent-path (.getParent (io/file file-path))
        {:keys [exit out]} (gradle parent-path
                                   "antq_list_repositories")]
    (when (= 0 exit)
      (->> (str/split-lines out)
           (filter #(str/starts-with? % "ANTQ;"))
           (map #(str/split % #";" 3))
           (reduce (fn [accm [_ repo-name url]]
                     (assoc accm repo-name {:url url})) {})))))

(defn- gradle-failure
  "Returns an exception for a failed Gradle `task`, with Gradle's output to help with diagnosis."
  [task {:keys [exit out err]}]
  (let [output (str/trim (str out "\n" err))]
    (ex-info (cond-> (str "Gradle task " task " failed with exit code " exit)
               (seq output) (str ":\n" output))
             {:exit exit})))

(defn- filter-deps-from-gradle-dependencies
  [file-path]
  (let [parent-path (.getParent (io/file file-path))
        {:keys [exit out] :as result} (gradle parent-path
                                              "--quiet"
                                              "dependencies")]
    (if (= 0 exit)
      (->> (str/split-lines out)
           (filter seq)
           (filter #(re-seq dep-regexp %))
           (map #(str/replace % dep-regexp ""))
           (map #(first (str/split % #" " 2)))
           (set))
      (throw (gradle-failure "dependencies" result)))))

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
    (let [repos (get-repositories absolute-file-path)
          deps (filter-deps-from-gradle-dependencies absolute-file-path)
          deps (keep #(convert-gradle-dependency relative-file-path %) deps)
          deps (map #(assoc % :repositories repos) deps)]
      deps)
    (catch Exception ex
      ;; Not chained with `ex`: a failing run reports only the root cause,
      ;; e.g. "error=2, No such file or directory" without the command name.
      (throw (ex-info (str "Failed to read " relative-file-path ": " (.getMessage ex))
                      (assoc (ex-data ex) :file relative-file-path))))))

(defn load-deps
  {:malli/schema [:function
                  [:=> :cat [:maybe r/?dependencies]]
                  [:=> [:cat 'string?] [:maybe r/?dependencies]]]}
  ([] (load-deps "."))
  ([dir]
   (let [file (io/file dir const.project-file/gradle)]
     (when (.exists file)
       (extract-deps (u.dep/relative-path file)
                     (.getAbsolutePath file))))))
