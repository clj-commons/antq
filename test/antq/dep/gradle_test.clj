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
    ;; NOTE: Gradle on local additionally detects `nrepl/nrepl`
    ;;       And also, gradle on GitHub Actions additionally detects `org.clojure/java.classpath`
    ;;       So we check only dependencies which is explicitly defined in buld.gradle.
    (t/is (every? #(contains? actual-deps %) defined-deps))))

(t/deftest extract-deps-without-repositories-test
  (let [deps (sut/extract-deps
              file-path
              (.getPath (io/resource "dep/no_repo_gradle/build.gradle")))
        defined-deps (->> defined-deps
                          (map #(assoc % :repositories nil))
                          (set))
        actual-deps (set deps)]
    (t/is (seq actual-deps))
    (t/is (every? #(contains? actual-deps %) defined-deps))))

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
              path)))))

(t/deftest extract-deps-command-error-test
  (with-redefs [sut/gradle-command "__non-existing-command__"]
    (let [deps (sut/extract-deps
                file-path
                (.getPath (io/resource "dep/build.gradle")))]
      (t/is (nil? deps)))))
