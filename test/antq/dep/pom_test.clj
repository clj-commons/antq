(ns antq.dep.pom-test
  (:require
   [antq.constant.project-file :as const.project-file]
   [antq.dep.pom :as sut]
   [antq.record :as r]
   [antq.test-report]
   [clojure.java.io :as io]
   [clojure.test :as t]))

(def ^:private file-path
  "path/to/pom.xml")

(defn- dependency
  [m]
  (r/map->Dependency (merge {:project :pom
                             :type :java
                             :file file-path
                             :repositories {"antq-test" {:url "s3://antq-repo/"}}}
                            m)))

(t/deftest extract-deps-test
  (let [deps (sut/extract-deps
              file-path
              (io/file (io/resource "dep/test_pom.xml")))]
    (t/is (sequential? deps))
    (t/is (every? #(instance? antq.record.Dependency %) deps))
    (t/is (= #{(dependency {:name "foo/core" :version "1.0.0"})
               (dependency {:name "foo/core" :version "1.1.0"})
               (dependency {:name "bar/bar" :version "2.0.0"})
               (dependency {:name "baz/baz" :version "3.0.0"})}
             (set deps)))))

(t/deftest extract-deps-with-properties-test
  (let [deps (sut/extract-deps
              file-path
              (io/file (io/resource "dep/test_pom_properties.xml")))
        dependency' (fn [m]
                      (assoc-in (dependency m)
                                [:repositories "central"]
                                {:url "https://repo.maven.apache.org/maven2"}))]
    (t/is (sequential? deps))
    (t/is (= #{(dependency' {:name "org.clojure/clojure" :version "1.4.0"})
               (dependency' {:name "org.tcrawley/dynapath" :version "1.0.0"})}
             (set deps)))))

(t/deftest extract-deps-with-parent-child-pom-test
  (let [deps (sut/extract-deps
              file-path
              (io/file (io/resource "dep/child_pom/child/pom.xml")))]
    (t/is (= [(assoc (dependency {:name "org.clojure/clojure" :version "1.5.0"})
                     :repositories {"central" {:url "https://repo.maven.apache.org/maven2"}})]
             deps))))

(t/deftest load-deps-test
  (with-redefs [const.project-file/maven "test_pom.xml"]
    (let [deps (sut/load-deps "test/resources/dep")]
      (t/is (seq deps))
      (t/is (every? #(= :java (:type %)) deps))))

  (with-redefs [const.project-file/maven "non_existing_file.edn"]
    (t/is (nil? (sut/load-deps "test/resources/dep")))))
