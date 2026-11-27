(ns antq.util.env-test
  (:require
   [antq.test-helper :as h]
   [antq.test-report]
   [antq.util.env :as sut]
   [clojure.test :as t]))

(t/deftest getlong-test
  (h/with-env {"FOO" "100"}
              (t/is (= 100 (sut/getlong "FOO" 200))))

  (h/with-env {"FOO" nil}
              (t/is (= 200 (sut/getlong "FOO" 200))))

  (h/with-env {"FOO" "INVALID"}
              (t/is (= 200 (sut/getlong "FOO" 200)))))
