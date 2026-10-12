(ns antq.util.report-test
  (:require
   [antq.test-report]
   [antq.util.report :as sut]
   [clojure.test :as t]))

(t/deftest skip-duplicated-file-name-test
  (t/is (= [{:file "foo"}
            {:file "bar"}
            {:file "baz"}]
           (sut/skip-duplicated-file-name
            [{:file "foo"}
             {:file "bar"}
             {:file "baz"}])))

  (t/is (= [{:file "foo"}
            {:file ""}
            {:file "bar"}]
           (sut/skip-duplicated-file-name
            [{:file "foo"}
             {:file "foo"}
             {:file "bar"}])))

  (t/is (= [{:file "bar"}
            {:file "baz"}
            {:file ""}]
           (sut/skip-duplicated-file-name
            [{:file "bar"}
             {:file "baz"}
             {:file "baz"}])))

  (t/is (= [{:file "foo"}
            {:file ""}
            {:file ""}
            {:file "bar"}]
           (sut/skip-duplicated-file-name
            [{:file "foo"}
             {:file ""}
             {:file "foo"}
             {:file "bar"}])))

  (t/is (= [{:file "foo"}
            {:file ""}
            {:file "bar"}
            {:file ""}]
           (sut/skip-duplicated-file-name
            [{:file "foo"}
             {:file ""}
             {:file "bar"}
             {:file "bar"}]))))
