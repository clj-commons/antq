(ns antq.dep.babashka-test
  (:require
   [antq.constant.project-file :as const.project-file]
   [antq.dep.babashka :as sut]
   [antq.record :as r]
   [antq.test-helper :as h]
   [clojure.test :as t]))

(t/deftest load-deps-test
  (with-redefs [const.project-file/babashka "test_bb.edn"]
    (t/is (= (->> [(r/map->Dependency {:type :java
                                       :file (h/os-path "test/resources/dep/test_bb.edn")
                                       :name "bb/core"
                                       :version "1.0.0"
                                       :project :clojure})
                   (r/map->Dependency {:type :java
                                       :file (h/os-path "test/resources/dep/test_bb.edn")
                                       :name "with/meta"
                                       :version "2.0.0"
                                       :project :clojure})
                   (r/map->Dependency {:type :java
                                       :file (h/os-path "test/resources/dep/test_bb.edn")
                                       :name "meta/range-ignore1"
                                       :version "4.0.0"
                                       :project :clojure
                                       :exclude-versions ["5.x"]})
                   (r/map->Dependency {:type :java
                                       :file (h/os-path "test/resources/dep/test_bb.edn")
                                       :name "meta/range-ignore2"
                                       :version "5.0.0"
                                       :project :clojure
                                       :exclude-versions ["6.x" "7.x"]})]
                  (sort-by :name))
             (->> (sut/load-deps "test/resources/dep")
                  (sort-by :name))))))
