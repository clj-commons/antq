(ns ^:no-doc antq.util.exception
  (:import
   clojure.lang.ExceptionInfo))

(defn ex-timeout
  [msg]
  (ex-info msg {:type ::timeout}))

(defn ex-timeout?
  [x]
  (and (instance? ExceptionInfo x)
       (= ::timeout (:type (ex-data x)))))
