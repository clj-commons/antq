(ns antq.dep.gradle-test
  (:require
   [antq.dep.gradle :as sut]
   [antq.record :as r]
   [antq.test-helper :as h]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :as t]))

(def ^:private file-path
  "path/to/build.gradle")

(def ^:private expected-repos
  {"MavenRepo" {:url "https://repo.maven.apache.org/maven2/"}
   "clojars" {:url "https://repo.clojars.org"}})

(defn- java-dependency
  [m]
  (r/map->Dependency (merge {:project :gradle
                             :type :java
                             :file file-path
                             :repositories expected-repos}
                            m)))

(def ^:private defined-deps
  [(java-dependency {:name "org.ajoberstar/jovial" :version "0.3.0"})
   (java-dependency {:name "org.clojure/tools.namespace" :version "1.0.0"})
   (java-dependency {:name "org.clojure/clojure" :version "1.10.0"})])

(t/deftest extract-deps-test
  (let [deps (sut/extract-deps
              file-path
              (.getPath (io/resource "dep/build.gradle")))
        defined-deps (set defined-deps)
        actual-deps (set deps)]
    (t/is (= defined-deps actual-deps))))

(t/deftest extract-deps-without-repository-task-test
  (let [deps (sut/extract-deps
              file-path
              (.getPath (io/resource "dep/no_repo_gradle/build.gradle")))
        actual-deps (set deps)]
    (t/testing "the repositories are found without the antq_list_repositories task"
      (t/is (every? #(contains? actual-deps %) defined-deps)))
    (t/testing "the dependency that the clojurephant plugin declares is listed too"
      (t/is (contains? actual-deps (java-dependency {:name "nrepl/nrepl" :version "0.9.0"}))))))

;; The fixture's comments say which dependencies are listed and which are not
(t/deftest extract-deps-multi-module-test
  (let [settings-file (io/resource "dep/gradle_multi_module/settings.gradle.kts")
        clojars {"clojars" {:url "https://repo.clojars.org"}}
        dep (fn [file dep-name version]
              (r/map->Dependency {:project :gradle
                                  :type :java
                                  :file file
                                  :name dep-name
                                  :version version
                                  :repositories clojars}))
        app-repos (assoc clojars "app-repo" {:url "https://example.com/maven/"})
        app-deps [(assoc (dep "path/to/app/build.gradle" "org.clojure/tools.namespace" "1.0.0") :repositories app-repos)
                  (assoc (dep "path/to/app/build.gradle" "org.clojure/data.json" "2.4.0") :repositories app-repos)]]
    (t/testing "all projects of the build, each dependency with its own project's file and repositories"
      (t/is (= (set (concat [(dep "path/to/lib/build.gradle.kts" "org.clojure/clojure" "1.10.0")
                             (dep "path/to/lib/build.gradle.kts" "com.fasterxml.jackson/jackson-bom" "2.12.0")
                             (dep "path/to/groovy-lib/build.gradle" "org.apache.groovy/groovy" "4.0.0")
                             (dep "path/to/scala-lib/build.gradle" "org.scala-lang/scala3-library_3" "3.3.0")]
                            app-deps))
               (set (sut/extract-deps "path/to/settings.gradle.kts" (.getPath settings-file))))))
    (t/testing "a subproject"
      (t/is (= (set (map #(assoc % :file "path/to/build.gradle") app-deps))
               (set (sut/extract-deps "path/to/build.gradle"
                                      (.getPath (io/resource "dep/gradle_multi_module/app/build.gradle")))))))
    (t/testing "a build with only settings.gradle.kts in its root directory is found"
      (t/is (= 6 (count (sut/load-deps (.getParent (io/file (.getPath settings-file))))))))))

;; The fixture's `gradlew` is a shell script
(when-not h/windows?
  (t/deftest find-gradle-wrapper-test
    (let [wrapper-path? #(and % (str/ends-with? % (h/os-path "dep/gradle_wrapper/gradlew")))]
      (t/testing "the wrapper in the project directory"
        (t/is (wrapper-path? (sut/find-gradle-wrapper (io/resource "dep/gradle_wrapper")))))
      (t/testing "the wrapper in the root project of a subproject"
        (t/is (wrapper-path? (sut/find-gradle-wrapper (io/resource "dep/gradle_wrapper/sub")))))
      (t/testing "no wrapper"
        (t/is (nil? (sut/find-gradle-wrapper (io/resource "dep")))))))

  (t/deftest extract-deps-with-gradle-wrapper-test
    (with-redefs [sut/gradle-command "__non-existing-command__"]
      (doseq [path ["dep/gradle_wrapper/build.gradle"
                    "dep/gradle_wrapper/sub/build.gradle"]]
        (t/is (= [(r/map->Dependency {:project :gradle
                                      :type :java
                                      :file file-path
                                      :name "org.example/from-wrapper"
                                      :version "1.0.0"
                                      :repositories {"wrapper-repo" {:url "https://example.com/maven/"}}})]
                 (sut/extract-deps file-path (.getPath (io/resource path))))
              path))))

  (t/deftest non-executable-gradle-wrapper-test
    ;; git does not reliably keep a file non-executable, so the project is created here
    (let [dir (.toFile (java.nio.file.Files/createTempDirectory
                        "antq-gradle" (make-array java.nio.file.attribute.FileAttribute 0)))
          build-file (io/file dir "build.gradle")
          wrapper (io/file dir "gradlew")]
      (try
        (spit build-file "")
        (spit wrapper "#!/bin/sh\n")
        (.setExecutable wrapper false)
        (t/is (thrown-with-msg? clojure.lang.ExceptionInfo #"Gradle wrapper is not executable"
                (sut/find-gradle-wrapper dir)))
        (with-redefs [sut/gradle-command "__non-existing-command__"]
          (t/is (thrown-with-msg? clojure.lang.ExceptionInfo #"Failed to read path/to/build.gradle: Gradle wrapper is not executable"
                  (sut/extract-deps file-path (.getPath build-file)))))
        (finally
          (run! io/delete-file [wrapper build-file dir])))))

  (t/deftest extract-deps-gradle-failure-test
    (let [ex (try
               (sut/extract-deps file-path (.getPath (io/resource "dep/gradle_failure/build.gradle")))
               nil
               (catch clojure.lang.ExceptionInfo ex ex))]
      (t/is (some? ex))
      (t/testing "the message includes Gradle's output"
        (t/is (str/includes? (ex-message ex) "Failed to read path/to/build.gradle: Gradle task antqDependencies failed with exit code 1"))
        (t/is (str/includes? (ex-message ex) "FAILURE: Build failed with an exception.")))
      (t/is (= {:exit 1 :file file-path} (ex-data ex)))))

  (t/deftest extract-deps-gradle-output-test
    (let [dep (fn [file dep-name version repos]
                (r/map->Dependency {:project :gradle
                                    :type :java
                                    :file file
                                    :name dep-name
                                    :version version
                                    :repositories repos}))]
      (t/testing "lines that the build prints itself are ignored, a dependency declared twice is listed once"
        (t/is (= [(dep file-path "org.example/root" "1.0.0"
                       {"settings-repo" {:url "https://settings.example.com/maven/"}
                        "root-repo" {:url "https://example.com/maven;jsessionid=1"}})
                  ;; a project's own repository replaces a settings repository with the same name
                  (dep "path/to/tools/cli/build.gradle" "org.example/cli" "2.0.0"
                       {"settings-repo" {:url "https://override.example.com/maven/"}})]
                 (sut/extract-deps file-path (.getPath (io/resource "dep/gradle_output/build.gradle"))))))))

  (t/deftest extract-deps-without-init-script-output-test
    (t/testing "no dependencies are reported when the init script did not work"
      (t/is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"Failed to read path/to/build.gradle: Gradle did not print the output of antq's init script"
              (sut/extract-deps file-path (.getPath (io/resource "dep/gradle_no_output/build.gradle")))))))

  (t/deftest init-script-file-deleted-test
    (let [init-script-files #(->> (.listFiles (io/file (System/getProperty "java.io.tmpdir")))
                                  (filter (fn [^java.io.File f]
                                            (re-matches #"antq-init.*\.gradle" (.getName f))))
                                  (set))
          before (init-script-files)]
      (sut/extract-deps file-path (.getPath (io/resource "dep/gradle_wrapper/build.gradle")))
      (t/is (thrown? clojure.lang.ExceptionInfo
              (sut/extract-deps file-path (.getPath (io/resource "dep/gradle_failure/build.gradle")))))
      (t/testing "the temporary init script is deleted, also when Gradle fails"
        (t/is (= before (init-script-files)))))))

(t/deftest extract-deps-command-error-test
  (with-redefs [sut/gradle-command "__non-existing-command__"]
    (t/is (thrown-with-msg? clojure.lang.ExceptionInfo #"Failed to read path/to/build.gradle"
            (sut/extract-deps
             file-path
             (.getPath (io/resource "dep/build.gradle")))))))
