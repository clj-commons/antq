(ns antq.dep.leiningen-test
  (:require
   [antq.constant.project-file :as const.project-file]
   [antq.dep.leiningen :as sut]
   [antq.record :as r]
   [antq.util.leiningen :as u.lein]
   [clojure.java.io :as io]
   [clojure.test :as t]))

(def ^:private file-path
  "path/to/project.clj")

(defn- dependency
  [m]
  (r/map->Dependency (merge {:project :leiningen
                             :type :java
                             :file file-path
                             :repositories {"antq-test" {:url "s3://antq-repo/"}
                                            "str-test" {:url "https://example.com"}}}
                            m)))

(t/deftest extract-deps-test
  (with-redefs [u.lein/lein-home (constantly "path/to/dummy/")]
    (let [deps (sut/extract-deps
                file-path
                (slurp (io/resource "dep/test_project.clj")))]
      (t/is (sequential? deps))
      (t/is (every? #(instance? antq.record.Dependency %) deps))
      (t/is (= (->> [(dependency {:name "foo/core" :version "1.0.0"})
                     (dependency {:name "foo/core" :version "1.1.0"})
                     (dependency {:name "bar/bar" :version "2.0.0"})
                     (dependency {:name "baz/baz" :version "3.0.0"})
                     (dependency {:name "plug/plug" :version "4.0.0"})
                     (dependency {:name "managed/dependency" :version "5.0.0"})
                     (dependency {:name "meta/range-ignore1" :version "6.0.0" :exclude-versions ["7.x"]})
                     (dependency {:name "meta/range-ignore2" :version "7.0.0" :exclude-versions ["8.x" "9.x"]})]
                    (sort-by :name))
               (sort-by :name deps))))))

(t/deftest load-deps-test
  (with-redefs [const.project-file/leiningen "test_project.clj"]
    (let [deps (sut/load-deps "test/resources/dep")]
      (t/is (seq deps))
      (t/is (every? #(= :java (:type %)) deps))))

  (with-redefs [const.project-file/leiningen "non_existing_file.edn"]
    (t/is (nil? (sut/load-deps "test/resources/dep")))))
