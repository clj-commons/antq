(ns antq.upgrade.leiningen-test
  (:require
   [antq.dep.leiningen :as dep.lein]
   [antq.record :as r]
   [antq.test-helper :as h]
   [antq.test-report]
   [antq.upgrade :as upgrade]
   [antq.upgrade.leiningen]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.test :as t]))

(t/deftest upgrade-dep-test
  (let [java-dep (r/map->Dependency {:project :leiningen
                                     :type :java
                                     :name "foo/core"
                                     :latest-version "9.0.0"
                                     :file (io/resource "dep/test_project.clj")})
        from-deps (->> java-dep
                       :file
                       (slurp)
                       (dep.lein/extract-deps ""))
        upgraded (upgrade/upgrader java-dep)
        to-deps (dep.lein/extract-deps "" upgraded)]
    (t/is (= #{{:name "foo/core" :version {:- "1.0.0" :+ "9.0.0"}}
               {:name "foo/core" :version {:- "1.1.0" :+ "9.0.0"}}}
             (h/diff-deps from-deps to-deps)))

    (let [upgraded-sexpr (edn/read-string upgraded)
          profiles (->> upgraded-sexpr
                        (drop-while #(not= :profiles %))
                        (second))]
      (t/testing "same name deps should be upgraded"
        (t/is (= '[:dependencies [[foo/core "9.0.0"]]]
                 (:same-name profiles))))

      (t/testing "excluded deps should not be upgraded"
        (t/is (= '[:dependencies [[foo/core "0.0.1"]]]
                 (:same-name-but-excluded profiles)))))))

(t/deftest upgrade-meta-dep-test
  (let [meta-dep (r/map->Dependency {:project :leiningen
                                     :type :java
                                     :name "bar/bar"
                                     :latest-version "8.0.0"
                                     :file (io/resource "dep/test_project.clj")})
        from-deps (->> meta-dep
                       :file
                       (slurp)
                       (dep.lein/extract-deps ""))
        to-deps (->> meta-dep
                     (upgrade/upgrader)
                     (dep.lein/extract-deps ""))]
    (t/is (= #{{:name "bar/bar" :version {:- "2.0.0" :+ "8.0.0"}}}
             (h/diff-deps from-deps to-deps)))))

(t/deftest upgrade-plugin-dep-test
  (let [meta-dep (r/map->Dependency {:project :leiningen
                                     :type :java
                                     :name "plug/plug"
                                     :latest-version "7.0.0"
                                     :file (io/resource "dep/test_project.clj")})
        from-deps (->> meta-dep
                       :file
                       (slurp)
                       (dep.lein/extract-deps ""))
        to-deps (->> meta-dep
                     (upgrade/upgrader)
                     (dep.lein/extract-deps ""))]
    (t/is (= #{{:name "plug/plug" :version {:- "4.0.0" :+ "7.0.0"}}}
             (h/diff-deps from-deps to-deps)))))
