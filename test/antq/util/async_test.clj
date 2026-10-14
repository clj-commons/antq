(ns antq.util.async-test
  (:require
   [antq.test-report]
   [antq.util.async :as sut]
   [antq.util.exception :as u.ex]
   [clojure.test :as t])
  (:import
   clojure.lang.ExceptionInfo))

(def ^:private test-async-fn
  (sut/fn-with-timeout
   (fn [x]
     (cond
       (= ::error x)
       (throw (Exception. "test error"))

       (= ::timeout x)
       (do (Thread/sleep 500)
           x)

       :else
       x))
   100
   "TIMEOUT"))

(t/deftest fn-with-timeout-test
  (t/is (= 10 (test-async-fn 10)))

  (try
    (test-async-fn ::timeout)
    (t/is false)
    (catch ExceptionInfo ex
      (t/is (true? (u.ex/ex-timeout? ex)))
      (t/is (= "TIMEOUT" (.getMessage ex))))
    (catch Throwable ex
      (t/is false (.getMessage ex))))

  (t/is (thrown-with-msg? Exception #"^test error$"
          (test-async-fn ::error))))
