(ns antq.util.env-test
  (:require
   [antq.test-report]
   [antq.util.env :as sut]
   [clojure.test :as t]))

(t/deftest getlong-test
  (with-redefs [sut/getenv (constantly "100")]
    (t/is (= 100 (sut/getlong "FOO" 200))))

  (with-redefs [sut/getenv (constantly nil)]
    (t/is (= 200 (sut/getlong "FOO" 200))))

  (with-redefs [sut/getenv (constantly "INVALID")]
    (t/is (= 200 (sut/getlong "FOO" 200)))))
