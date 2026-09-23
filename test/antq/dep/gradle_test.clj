(ns antq.dep.gradle-test
  (:require
   [antq.dep.gradle :as sut]
   [antq.record :as r]
   [clojure.java.io :as io]
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
    ;;       So we check only dependencies which is explicitly defined in build.gradle.
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

(t/deftest extract-deps-command-error-test
  (with-redefs [sut/gradle-command "__non-existing-command__"]
    (let [deps (sut/extract-deps
                file-path
                (.getPath (io/resource "dep/build.gradle")))]
      (t/is (nil? deps)))))
