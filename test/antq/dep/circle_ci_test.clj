(ns antq.dep.circle-ci-test
  (:require
   [antq.dep.circle-ci :as sut]
   [antq.record :as r]
   [clojure.java.io :as io]
   [clojure.test :as t]))

(defn- circle-ci-orb-dependency
  [m]
  (r/map->Dependency (merge {:project :circle-ci
                             :type :circle-ci-orb
                             :file "dep/test_circle_ci.yml"} m)))

(t/deftest extract-deps-test
  (let [deps (sut/extract-deps "dep/test_circle_ci.yml"
                               (slurp (io/resource "dep/test_circle_ci.yml")))]
    (t/is (sequential? deps))
    (t/is (every? #(instance? antq.record.Dependency %) deps))
    (t/is (= #{(circle-ci-orb-dependency {:name "circleci/node" :version "6.3.0"})
               (circle-ci-orb-dependency {:name "circleci/docker" :version "2.8.0"})}
             (set deps)))))
