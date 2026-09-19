(ns antq.dep.clojure-test
  (:require
   [antq.constant.project-file :as const.project-file]
   [antq.dep.clojure :as sut]
   [antq.record :as r]
   [clojure.java.io :as io]
   [clojure.test :as t]
   [clojure.tools.deps.edn :as deps-edn]))

(def ^:private file-path
  ;; "path/to/deps.edn"
  (.getAbsolutePath (io/file (io/resource "dep/test_deps.edn"))))

(defn- java-dependency
  [m]
  (r/map->Dependency (merge {:project :clojure
                             :type :java
                             :file file-path
                             :repositories {"antq-test" {:url "s3://antq-repo/"}}}
                            m)))
(defn- git-sha-dependency
  [m]
  (r/map->Dependency (merge {:project :clojure
                             :type :git-sha
                             :file file-path
                             :repositories {"antq-test" {:url "s3://antq-repo/"}}}
                            m)))

(defn- git-tag-dependency
  [m]
  (r/map->Dependency (merge {:project :clojure
                             :type :git-tag-and-sha
                             :file file-path
                             :repositories {"antq-test" {:url "s3://antq-repo/"}}}
                            m)))

(t/deftest extract-deps-test
  (with-redefs [const.project-file/clojure-cli "test_deps.edn"]
    (let [deps (sut/extract-deps
                file-path
                (slurp file-path))]
      (t/is (sequential? deps))
      (t/is (every? #(instance? antq.record.Dependency %) deps))
      (t/is (= #{(java-dependency {:name "foo/core" :version "1.0.0"})
                 (java-dependency {:name "foo/core" :version "1.1.0"})
                 (java-dependency {:name "bar/bar" :version "2.0.0"})
                 (java-dependency {:name "baz/baz" :version "3.0.0"})
                 (java-dependency {:name "rep/rep" :version "4.0.0"})
                 (java-dependency {:name "ovr/ovr" :version "5.0.0"})
                 (java-dependency {:name "dft/dft" :version "6.0.0"})
                 (java-dependency {:name "short-meta/short-meta" :version "2.5.8"})
                 (java-dependency {:name "full-meta/full-meta" :version "2.6.9"})
                 (java-dependency {:name "meta/range-ignore1" :version "4.6.9"
                                   :exclude-versions ["5.x"]})
                 (java-dependency {:name "meta/range-ignore2" :version "4.6.9"
                                   :exclude-versions ["6.x" "7.x"]})
                 (git-sha-dependency {:name "sha/sha" :version "dummy-sha"
                                      :extra {:url "https://github.com/example/sha.git"}})
                 (git-sha-dependency {:name "git-sha/git-sha" :version "dummy-git-sha"
                                      :extra {:url "https://github.com/example/git-sha.git"}})
                 (git-tag-dependency {:name "tag-short-sha/tag-short-sha" :version "v1.2.3"
                                      :extra {:url "https://github.com/example/tag-short.git"
                                              :sha "123abcd"}})
                 (git-tag-dependency {:name "git-tag-long-sha/git-tag-long-sha" :version "v2.3.4"
                                      :extra {:url "https://github.com/example/git-tag-long.git"
                                              :sha "1234567890abcdefghijklmnopqrstuvwxyz1234"}})
                 (git-sha-dependency {:name "com.github.liquidz/dummy"
                                      :version "dummy-inferring-url"
                                      :extra {:url "https://github.com/liquidz/dummy.git"}})
                 (java-dependency {:name "local/core" :version "9.9.9"
                                   :file (.getAbsolutePath (io/file (io/resource "dep/local/test_deps.edn")))
                                   :repositories nil})
                 (java-dependency {:name "local/nested-core" :version "8.8.8"
                                   :file (.getAbsolutePath (io/file (io/resource "dep/local/nested/test_deps.edn")))
                                   :repositories nil})
                 (java-dependency {:name "namespaced/mvn" :version "1.0.0"})
                 (git-sha-dependency {:name "namespaced/sha" :version "1234567890abcdefghijklmnopqrstuvwxyz1234"
                                      :extra {:url "https://github.com/example/git-sha.git"}})
                 (git-tag-dependency {:name "namespaced/tag-and-sha" :version "v1.2.3"
                                      :extra {:url "https://github.com/example/tag-short.git"
                                              :sha "123abcd"}})}
               (set deps))))))

(t/deftest extract-deps-cross-project-configuration-test
  (let [cross-project-path (.getAbsolutePath
                            (io/file
                             (.getParentFile (io/file (io/resource "dep/test_deps.edn")))
                             "cross-project"
                             "test_deps.edn"))
        content (pr-str '{:deps {foo/bar {:mvn/version "0.0.1"}}})]
    (with-redefs [deps-edn/user-deps-path (constantly cross-project-path)]
      (t/is (= [(java-dependency
                 {:name "foo/bar"
                  :version "0.0.1"
                  :file "dummy"
                  :repositories {"cross-project" {:url "https://cross-project.example.com"}}})]
               (sut/extract-deps "dummy" content))))))

(t/deftest extract-deps-unexpected-test
  (t/is (empty? (sut/extract-deps file-path "[:deps \"foo\"]")))
  (t/is (empty? (sut/extract-deps file-path "{:deps \"foo\"}")))
  (t/is (empty? (sut/extract-deps file-path "{:deps {foo/core \"bar\"}}"))))

(t/deftest load-deps-test
  (with-redefs [const.project-file/clojure-cli "test_deps.edn"]
    (let [deps (sut/load-deps "test/resources/dep")]
      (t/is (seq deps))
      (t/is (every? #(contains? #{:java :git-sha :git-tag-and-sha} (:type %)) deps))))

  (with-redefs [const.project-file/clojure-cli "non_existing_file.edn"]
    (t/is (nil? (sut/load-deps "test/resources/dep")))))
