(ns antq.ver.java-test
  (:require
   [antq.record :as r]
   [antq.util.exception :as u.ex]
   [antq.util.maven :as u.mvn]
   [antq.ver :as ver]
   [antq.ver.java :as sut]
   [clojure.edn :as edn]
   [clojure.set :as set]
   [clojure.test :as t]))

(def ^:private current-clojure-version
  (get-in (edn/read-string (slurp "deps.edn"))
          [:deps 'org.clojure/clojure :mvn/version]))

(t/deftest get-versions-test
  (let [vers (#'sut/get-versions 'org.clojure/clojure
                                 {:repositories u.mvn/default-repos})]
    (t/is (seq vers))
    (t/is (contains? (set (map str vers)) current-clojure-version))))

(defn- dummy-versions
  [_ opts]
  (concat
   ["1"]
   (when (contains? (:repositories opts) "dummy")
     ["1.3"])
   ["1.6-SNAPSHOT" "2"]))

(defn- get-sorted-versions
  [m]
  (ver/get-sorted-versions (r/map->Dependency (assoc m :type :java :name "dummy"))
                           {}))

(t/deftest get-sorted-versions-test
  (with-redefs [sut/get-versions-with-timeout dummy-versions]
    (t/is (= ["2" "1"]
             (get-sorted-versions {:version "1.0.0"})))
    (t/is (= ["2" "1.6-SNAPSHOT" "1"]
             (get-sorted-versions {:version "1.0.0-SNAPSHOT"})))
    (t/is (= ["2" "1.6-SNAPSHOT" "1"]
             (get-sorted-versions {:version "1.0.0-snapshot"}))))

  (t/testing "normalizing repository URL"
    (with-redefs [sut/get-sorted-versions-by-name (fn [_ opts _] opts)]
      (let [res (get-sorted-versions {:repositories {"foo" {:url "s3p://bar"}}})
            diff (set/difference (set (:repositories res))
                                 (set u.mvn/default-repos))]
        (t/is (= #{["foo" {:url "s3://bar"}]}
                 diff))))))

(t/deftest get-sorted-versions-timestamped-snapshot-test
  (with-redefs [sut/get-versions-with-timeout (constantly ["1" "1.6-20240101.120000-1" "2"])]
    (let [get-sorted-versions #(ver/get-sorted-versions
                                (r/map->Dependency {:type :java :name "dummy-timestamped" :version %})
                                {})]
      (t/is (= ["2" "1"]
               (get-sorted-versions "1.0.0")))
      (t/is (= ["2" "1.6-20240101.120000-1" "1"]
               (get-sorted-versions "1.0.0-SNAPSHOT")))
      (t/is (= ["2" "1.6-20240101.120000-1" "1"]
               (get-sorted-versions "1.5-20231201.090000-3"))))))

(t/deftest get-sorted-versions-timeout-test
  (with-redefs [sut/get-sorted-versions-by-name sut/get-sorted-versions-by-name*
                sut/get-versions-with-timeout (fn [& _] (throw (u.ex/ex-timeout "test timeout")))]
    (let [deps (get-sorted-versions {:version "1.0.0"})]
      (t/is (= 1 (count deps)))
      (t/is (u.ex/ex-timeout? (first deps))))))
