(ns ^:gradle antq.dep.gradle-test
  (:require
   [antq.dep.gradle :as sut]
   [antq.record :as r]
   [antq.test-helper :as h]
   [antq.util.os :as os]
   [babashka.fs :as fs]
   [clojure.string :as str]
   [clojure.test :as t]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test])
  (:import
   (java.util.regex
    Pattern)))

(def ^:private file-path
  (h/os-path "path/to/build.gradle"))

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

(defn- tdir
  [test-name]
  (str (fs/file "target/test/gradle" test-name)))

(t/deftest extract-deps-test
  (let [test-dir (tdir "extract-deps-test")
        _ (h/setup-deps-scenario test-dir [["build.gradle"]])
        deps (sut/extract-deps
              file-path
              (str (fs/path test-dir "build.gradle")))
        defined-deps (set defined-deps)
        actual-deps (set deps)]
    (t/is (match? (m/equals defined-deps) actual-deps))))

(t/deftest extract-deps-without-repository-task-test
  (let [test-dir (tdir "extract-deps-without-repository-task-test")
        _ (h/setup-deps-scenario test-dir [["no_repo_gradle"]])
        deps (sut/extract-deps
              file-path
              (str (fs/path test-dir "no_repo_gradle" "build.gradle")))
        actual-deps (set deps)]
    (t/testing "the repositories are found without the antq_list_repositories task"
      (t/is (every? #(contains? actual-deps %) defined-deps)))
    (t/testing "the dependency that the clojurephant plugin declares is listed too"
      (t/is (contains? actual-deps (java-dependency {:name "nrepl/nrepl" :version "0.9.0"}))))))

;; The fixture's comments say which dependencies are listed and which are not
(t/deftest extract-deps-multi-module-test
  (let [test-dir (tdir "extract-deps-multi-module-test")
        _ (h/setup-deps-scenario test-dir [["gradle_multi_module"]])
        settings-file (str (fs/path test-dir "gradle_multi_module" "settings.gradle.kts"))
        clojars {"clojars" {:url "https://repo.clojars.org"}}
        dep (fn [file dep-name version]
              (r/map->Dependency {:project :gradle
                                  :type :java
                                  :file (h/os-path file)
                                  :name dep-name
                                  :version version
                                  :repositories clojars}))
        app-repos (assoc clojars "app-repo" {:url "https://example.com/maven/"})
        app-deps [(assoc (dep "path/to/app/build.gradle" "org.clojure/tools.namespace" "1.0.0") :repositories app-repos)
                  (assoc (dep "path/to/app/build.gradle" "org.clojure/data.json" "2.4.0") :repositories app-repos)]]
    (t/testing "all projects of the build, each dependency with its own project's file and repositories"
      (t/is (match? (m/nested-equals
                     (set (concat [(dep "path/to/lib/build.gradle.kts" "org.clojure/clojure" "1.10.0")
                                   (dep "path/to/lib/build.gradle.kts" "com.fasterxml.jackson/jackson-bom" "2.12.0")
                                   (dep "path/to/groovy-lib/build.gradle" "org.apache.groovy/groovy" "4.0.0")
                                   (dep "path/to/scala-lib/build.gradle" "org.scala-lang/scala3-library_3" "3.3.0")]
                                  app-deps)))
                    (set (sut/extract-deps "path/to/settings.gradle.kts" settings-file)))))
    (t/testing "a subproject"
      (t/is (match? (m/nested-equals
                     (set (map #(assoc % :file (h/os-path "path/to/build.gradle")) app-deps)))
                    (set (sut/extract-deps "path/to/build.gradle"
                                           (fs/path test-dir "gradle_multi_module" "app" "build.gradle"))))))
    (t/testing "a build with only settings.gradle.kts in its root directory is found"
      (t/is (= 6 (count (sut/load-deps (fs/parent settings-file))))))))

(t/deftest find-gradle-wrapper-test
  (let [test-dir (tdir "find-gradle-wrapper-test")
        _ (h/setup-deps-scenario test-dir [["gradle_wrapper"]])
        wrapper-path? #(and % (str/ends-with? % (str (h/os-path "/gradle_wrapper/gradlew")
                                                     (when (os/windows?) ".bat"))))]
    (t/testing "the wrapper in the project directory"
      (t/is (wrapper-path? (sut/find-gradle-wrapper (fs/path test-dir "gradle_wrapper")))))
    (t/testing "the wrapper in the root project of a subproject"
      (t/is (wrapper-path? (sut/find-gradle-wrapper (fs/path test-dir "gradle_wrapper" "sub")))))
    (t/testing "no wrapper"
      (t/is (nil? (sut/find-gradle-wrapper test-dir))))))

(t/deftest extract-deps-with-gradle-wrapper-test
  (let [test-dir (tdir "extract-deps-with-gradle-wrapper-test")]
    (h/setup-deps-scenario test-dir [["gradle_wrapper"]])
    (with-redefs [sut/gradle-command "__non-existing-command__"]
      (doseq [path ["build.gradle"
                    "sub/build.gradle"]]
        (t/is (= [(r/map->Dependency {:project :gradle
                                      :type :java
                                      :file (h/os-path file-path)
                                      :name "org.example/from-wrapper"
                                      :version "1.0.0"
                                      :repositories {"wrapper-repo" {:url "https://example.com/maven/"}}})]
                 (sut/extract-deps file-path (fs/path test-dir "gradle_wrapper" path)))
              path)))))

(when-not (os/windows?)
  ;; .bat files, I think, are always executable, so skip for Windows
  (t/deftest non-executable-gradle-wrapper-test
    ;; git does not reliably keep a file non-executable, so the project is created here
    (let [test-dir (tdir "non-executable-gradle-wrapper-test")]
      (h/setup-deps-scenario test-dir [["gradle_wrapper"]])
      (fs/set-posix-file-permissions (fs/file test-dir "gradle_wrapper" "gradlew") "rw-rw-rw-")
      (t/is (thrown-with-msg? clojure.lang.ExceptionInfo #"Gradle wrapper is not executable"
              (sut/find-gradle-wrapper (fs/path test-dir "gradle_wrapper")))))))

(t/deftest extract-deps-gradle-failure-test
  (let [test-dir (tdir "extract-deps-gradle-failure-test")
        _ (h/setup-deps-scenario test-dir [["gradle_failure"]])
        ex (try
             (sut/extract-deps file-path (fs/path test-dir "gradle_failure" "build.gradle"))
             nil
             (catch clojure.lang.ExceptionInfo ex ex))]
    (t/is (some? ex))
    (t/testing "the message includes Gradle's output"
      (t/is (str/includes? (ex-message ex) (str "Failed to read " (h/os-path "path/to/build.gradle")
                                                ": Gradle task antqDependencies failed with exit code 1")))
      (t/is (str/includes? (ex-message ex) "FAILURE: Build failed with an exception.")))
    (t/is (= {:exit 1 :file file-path} (ex-data ex)))))

(t/deftest extract-deps-gradle-output-test
  (let [test-dir (tdir "extract-deps-gradle-output-test")
        _ (h/setup-deps-scenario test-dir [["gradle_output"]])
        dep (fn [file dep-name version repos]
              (r/map->Dependency {:project :gradle
                                  :type :java
                                  :file (h/os-path file)
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
               (sut/extract-deps file-path (fs/path test-dir "gradle_output" "build.gradle")))))))

(t/deftest extract-deps-without-init-script-output-test
  (t/testing "no dependencies are reported when the init script did not work"
    (let [test-dir (tdir "extract-deps-without-init-script-ouput-test")
          _ (h/setup-deps-scenario test-dir [["gradle_no_output"]])
          ex (try
               (sut/extract-deps file-path (fs/path test-dir "gradle_no_output" "build.gradle"))
               nil
               (catch clojure.lang.ExceptionInfo ex ex))]
      (t/is (some? ex))
      (t/is (str/includes? (ex-message ex)
                           (str "Failed to read " (h/os-path "path/to/build.gradle")
                                ": Gradle did not print the output of antq's init script"))))))

(t/deftest init-script-file-deleted-test
  (let [test-dir (tdir "init-script-file-deleted-test")
        _ (h/setup-deps-scenario test-dir [["gradle_wrapper"]])
        init-script-files #(->> (fs/list-dir (fs/temp-dir))
                                (filter (fn [f]
                                          (re-matches #"antq-init.*\.gradle" (fs/file-name f))))
                                (set))
        before (init-script-files)]
    (sut/extract-deps file-path (fs/path test-dir  "gradle_wrapper" "build.gradle"))
    (t/is (thrown? clojure.lang.ExceptionInfo
            (sut/extract-deps file-path (fs/path test-dir "build.gradle"))))
    (t/testing "the temporary init script is deleted, also when Gradle fails"
      (t/is (= before (init-script-files))))))

(t/deftest extract-deps-command-error-test
  (with-redefs [sut/gradle-command "__non-existing-command__"]
    (t/is (thrown-with-msg? clojure.lang.ExceptionInfo
                            (re-pattern (str "Failed to read " (Pattern/quote file-path)))
            (sut/extract-deps
             file-path
             "build.gradle")))))
