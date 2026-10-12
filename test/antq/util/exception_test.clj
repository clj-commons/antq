(ns antq.util.exception-test
  (:require
   [antq.test-report]
   [antq.util.exception :as sut]
   [clojure.test :as t])
  (:import
   clojure.lang.ExceptionInfo))

(t/deftest ex-timeout-test
  (let [ex (sut/ex-timeout "foo")]
    (t/is (instance? ExceptionInfo ex))
    (t/is (= "foo" (.getMessage ex)))
    (t/is (contains? (ex-data ex) :type))
    (t/is (qualified-keyword? (:type (ex-data ex))))))

(t/deftest ex-timeout?-test
  (t/is (true? (sut/ex-timeout? (sut/ex-timeout ""))))
  (t/is (false? (sut/ex-timeout? (ex-info "" {}))))
  (t/is (false? (sut/ex-timeout? nil))))
