(ns antq.dep.shadow-test
  (:require
   [antq.constant.project-file :as const.project-file]
   [antq.dep.shadow :as sut]
   [antq.record :as r]
   [antq.test-report]
   [antq.util.env :as u.env]
   [clojure.java.io :as io]
   [clojure.test :as t]))

(def ^:private file-path
  "path/to/shadow-cljs.edn")

(defn- dependency
  [m]
  (r/map->Dependency (merge {:project :shadow-cljs
                             :type :java
                             :file file-path}
                            m)))

(t/deftest extract-deps-test
  (let [deps (sut/extract-deps
              file-path
              (slurp (io/resource "dep/test_shadow-cljs.edn")))]
    (t/is (sequential? deps))
    (t/is (every? #(instance? antq.record.Dependency %) deps))
    (t/is (= (->> [(dependency {:name "foo/core" :version "1.0.0"})
                   (dependency {:name "bar" :version "2.0.0"})
                   (dependency {:name "baz" :version "3.0.0"})
                   (dependency {:name "with/meta" :version "4.0.0"})
                   (dependency {:name "meta/range-ignore1" :version "6.0.0" :exclude-versions ["7.x"]})
                   (dependency {:name "meta/range-ignore2" :version "7.0.0" :exclude-versions ["8.x" "9.x"]})]
                  (sort-by :name))
             (sort-by :name deps)))))

(t/deftest extract-deps-with-env-tag-test
  (with-redefs [u.env/getenv  {"ENV1" "1.0.0"
                               "ENV2" "2.0.0"
                               "ENV5" "5.0.0"}]

    (let [deps (sut/extract-deps
                file-path
                (slurp (io/resource "dep/test_shadow-cljs-env.edn")))]
      (t/is (sequential? deps))
      (t/is (every? #(instance? antq.record.Dependency %) deps))
      (t/is (= (->> [(dependency {:name "foo1" :version "1.0.0"})
                     (dependency {:name "foo2" :version "2.0.0"})
                     (dependency {:name "foo3" :version "default3"})
                     (dependency {:name "foo4" :version "default4"})
                     (dependency {:name "foo5" :version "5.0.0"})]
                    (sort-by :name))
               (sort-by :name deps))))))

(t/deftest load-deps-test
  (with-redefs [const.project-file/shadow-cljs "test_shadow-cljs.edn"]
    (let [deps (sut/load-deps "test/resources/dep")]
      (t/is (seq deps))
      (t/is (every? #(= :java (:type %)) deps))))

  (with-redefs [const.project-file/shadow-cljs "non_existing_file.edn"]
    (t/is (nil? (sut/load-deps "test/resources/dep")))))
