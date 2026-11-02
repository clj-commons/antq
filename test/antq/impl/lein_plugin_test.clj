(ns antq.impl.lein-plugin-test
  (:require
   [antq.impl.lein-plugin :as sut]
   [antq.report :as report]
   [antq.test-report]
   [antq.util.maven :as u.maven]
   [antq.ver :as ver]
   [babashka.fs :as fs]
   [clojure.edn :as edn]
   [clojure.test :as t])
  (:import
   (java.time
    Duration
    Instant)))

(t/deftest antq-exit-cooldown-test
  (let [result-file (str (fs/create-temp-file))
        exit (fn [deps options]
               (with-redefs [ver/get-sorted-versions (constantly ["2.0.0" "1.0.0"])
                             u.maven/release-date (constantly (.minus (Instant/now) (Duration/ofDays 1)))]
                 (sut/antq result-file
                           {:dependencies (mapv #(hash-map :dep %) deps)}
                           (assoc options :reporter report/no-output-reporter)))
               (:exit (edn/read-string (slurp result-file))))]
    (try
      (t/testing "a dep held by the cooldown gives exit code 0"
        (t/is (= 0 (exit [['lein-plugin/held "1.0.0"]] {:cooldown "7d"})))
        (t/is (= 1 (exit [['lein-plugin/held "1.0.0"]] {}))))
      (finally
        (fs/delete-if-exists result-file)))))
