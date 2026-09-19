(ns antq.upgrade.circle-ci-test
  (:require
   [antq.dep.circle-ci :as dep.circle-ci]
   [antq.record :as r]
   [antq.test-helper :as h]
   [antq.upgrade :as upgrade]
   [antq.upgrade.circle-ci]
   [clojure.java.io :as io]
   [clojure.test :as t]))

(def ^:private node-dep
  (r/map->Dependency {:project :circle-ci
                      :type :circle-ci-orb
                      :name "circleci/node"
                      :version "6.3.0"
                      :latest-version "7.0.0"
                      :file (io/resource "dep/test_circle_ci.yml")}))

(t/deftest upgrade-dep-test
  (t/testing "supported"
    (let [from-deps (->> (:file node-dep)
                         (slurp)
                         (dep.circle-ci/extract-deps ""))
          temp-content (->> node-dep
                            (upgrade/upgrader))
          to-deps (h/with-temp-file
                   [temp-file temp-content]
                   (->> (assoc node-dep
                               :version "7.0.0"
                               :file temp-file)
                        (upgrade/upgrader)
                        (dep.circle-ci/extract-deps "")))]
      (t/is (= #{{:name "circleci/node" :version {:- "6.3.0" :+ "7.0.0"}}}
               (h/diff-deps from-deps to-deps))))))
