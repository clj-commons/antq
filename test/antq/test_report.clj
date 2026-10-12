(ns antq.test-report
  (:require
   [clojure.test]))

(def platform
  (if-let [bb-version (System/getProperty "babashka.version")]
    (str "bb " bb-version)
    (str "jdk " (System/getProperty "java.version") " clj " (clojure-version))))

(defmethod clojure.test/report :begin-test-var [m]
  (let [test-name (-> m :var meta :name)]
    (println (format "=== %s [%s]" test-name platform))))
