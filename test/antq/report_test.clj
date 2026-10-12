(ns antq.report-test
  (:require
   [antq.report :as sut]
   [antq.test-report]
   [clojure.test :as t]))

(t/deftest unknown-reporter-test
  (t/is (nil? (sut/reporter {} {:reporter :unknown-reporter}))))
