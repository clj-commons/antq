(ns antq.util.function-test
  (:require
   [antq.test-report]
   [antq.util.function :as sut]
   [clojure.test :as t]))

(defn- test-fn*
  [m counter-atom]
  (assoc m :count (swap! counter-atom inc)))

(t/deftest memoize-by-test
  (let [test-fn (sut/memoize-by test-fn* :name)
        counter (atom 0)]
    (t/testing "Should be counted because the result is not cached"
      (t/is (= {:name "alice" :count 1}
               (test-fn {:name "alice"} counter))))

    (t/testing "Should not be counted because the result is cached"
      (t/is (= {:name "alice" :count 1}
               (test-fn {:name "alice"} counter))))

    (t/testing "Should be counted because the result is not cached"
      (t/is (= {:name "bob" :count 2}
               (test-fn {:name "bob"} counter))))))
