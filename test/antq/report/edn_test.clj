(ns antq.report.edn-test
  (:require
   [antq.report :as report]
   [antq.report.edn]
   [antq.test-helper :as h]
   [antq.test-report]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.test :as t]))

(t/deftest edn-reporter-test
  (let [deps [(h/test-dep {:file "a" :name "foo" :version "1" :latest-version "2"})
              (h/test-dep {:file "b" :name "bar" :version "2" :latest-version "3"
                           :changes-url "https://example.com"})]
        res (str/trim
             (with-out-str
               (report/reporter deps {:reporter "edn"})))]
    (t/is (seq res))
    (t/is (= [{:file "a"
               :name "foo"
               :version "1"
               :latest-version "2"
               :type :test
               :project nil
               :latest-name nil
               :only-newest-version? nil
               :repositories nil
               :changes-url nil
               :diff-url nil
               :exclude-versions nil
               :parent nil}
              {:file "b"
               :name "bar"
               :version "2"
               :latest-version "3"
               :type :test
               :project nil
               :latest-name nil
               :only-newest-version? nil
               :repositories nil
               :changes-url "https://example.com"
               :diff-url "https://example.com"
               :exclude-versions nil
               :parent nil}]
             (sort-by :file (edn/read-string res))))))
