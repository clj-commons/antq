(ns antq.report.table-test
  (:require
   [antq.report :as report]
   [antq.report.table]
   [antq.test-helper :as h]
   [antq.test-report]
   [clojure.string :as str]
   [clojure.test :as t]))

(defn- reporter
  [deps]
  (report/reporter deps {:reporter "table"}))

(t/deftest reporter-test
  (let [dummy-deps [(h/test-dep {:file "a" :name "foo" :version "1" :latest-version "2"})
                    (h/test-dep {:file "b" :name "bar" :version "1" :latest-version nil})
                    (h/test-dep {:file "c" :name "baz" :version "2" :latest-version "3" :changes-url "here"})
                    (h/test-dep {:file "c" :name "old" :version "1" :latest-version nil :latest-name "new"})]
        outputs (str/split-lines (with-out-str (reporter dummy-deps)))]
    (t/is (some #(and (str/includes? % "foo") (str/includes? % "1") (str/includes? % "2")) outputs))
    (t/is (some #(and (str/includes? % "bar") (str/includes? % "Failed to fetch")) outputs))
    (t/is (some #(and (str/includes? % "baz") (str/includes? % "2") (str/includes? % "3")) outputs))
    (t/is (some #(and (str/includes? % "old") (str/includes? % "new")) outputs))
    (t/is (some #(str/includes? % "here") outputs))

    ;; no outdated deps
    (t/is (str/includes? (with-out-str (reporter []))
                         "All dependencies are up-to-date"))))
