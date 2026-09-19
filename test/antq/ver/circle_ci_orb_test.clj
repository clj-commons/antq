(ns antq.ver.circle-ci-orb-test
  (:require
   [antq.record :as r]
   [antq.ver :as ver]
   [antq.ver.circle-ci-orb :as sut]
   [clojure.test :as t]))

(defn- dep
  [m]
  (r/map->Dependency (merge {:type :circle-ci-orb} m)))

(defn- orb-id
  [orb-ns orb-name]
  (get-in {"circleci" {"node" "circleci-node-id"}} [orb-ns orb-name]))

(defn- orb-versions
  [id]
  (get {"circleci-node-id" ["3.0.0" "2.0.0" "1.0.0"]} id))

(t/deftest get-sorted-versions-test
  (with-redefs [sut/orb-id orb-id
                sut/orb-versions orb-versions]
    (t/is (= ["3.0.0" "2.0.0" "1.0.0"]
             (ver/get-sorted-versions (dep {:name "circleci/node"
                                            :version "1.0.0"})
                                      {})))))
