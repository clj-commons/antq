(ns antq.upgrade-test
  (:require
   [antq.record :as r]
   [antq.report :as report]
   [antq.test-report]
   [antq.upgrade :as sut]
   [antq.util.exception :as u.ex]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :as t]))

(def temp-files
  (repeatedly 3 #(io/file (str "." (gensym)))))

(defn- create-temp-file
  []
  (dotimes [n (count temp-files)]
    (spit (nth temp-files n) (str "before" n))))

(defn- delete-temp-file
  []
  (doseq [^java.io.File f temp-files]
    (when (.exists f)
      (.delete f))))

(defn temp-file-fixture
  [f]
  (create-temp-file)
  (f)
  (delete-temp-file))

(t/use-fixtures :each temp-file-fixture)

(defmethod sut/upgrader ::test
  [dep]
  (str "after" (:latest-version dep)))

(t/deftest upgrade!-test
  (let [[temp1 temp2 temp3] temp-files
        dep1 (r/map->Dependency {:project ::test
                                 :latest-version "LATEST"
                                 :file temp1})
        ;; should be skipped because latest-version is nil
        dep2 (r/map->Dependency {:project ::test
                                 :latest-version nil
                                 :file temp2})
        ;; should be skipped because latest-version is ex-timeout
        dep3 (r/map->Dependency {:project ::test
                                 :latest-version (u.ex/ex-timeout "test")
                                 :file temp3})]
    (t/is (every? #(= 0 (.indexOf % "before"))
                  (map slurp temp-files)))

    (sut/upgrade! [dep1 dep2 dep3] {:force true})

    (t/is (= "afterLATEST" (slurp temp1)))
    (t/is (= "before1" (slurp temp2)))
    (t/is (= "before2" (slurp temp3)))))

(t/deftest upgrade!-cooldown-test
  (let [[temp1 temp2 temp3] temp-files
        two-days-ago (str (.minus (java.time.Instant/now) (java.time.Duration/ofDays 2)))
        dep {:project ::test :name "foo" :version "1" :latest-version "3" :cooldown "7d"}
        out (with-out-str
              (sut/upgrade! [(r/map->Dependency (assoc dep :file temp1 :cooldown-version "2"
                                                       :latest-released-at two-days-ago))
                             (r/map->Dependency (assoc dep :file temp2 :name "bar" :cooldown-version nil))
                             (r/map->Dependency (assoc dep :file temp3 :name "baz" :cooldown-version "3"))]
                            {:force true}))]
    (t/is (= "after2" (slurp temp1)))
    (t/is (= "before1" (slurp temp2)))
    (t/is (= "after3" (slurp temp3)))
    (t/is (= ["Skipped foo 3 (2d old, cooldown 7d)"
              "Skipped bar 3 (no release date, cooldown 7d)"
              (format "Upgraded foo '1' to '2' in %s." temp1)
              (format "Upgraded baz '1' to '3' in %s." temp3)]
             (filter #(re-find #"^(Skipped|Upgraded)" %) (str/split-lines out))))

    (t/testing "nothing is printed with the no-output reporter (antq.api)"
      (t/is (= "" (with-out-str
                    (sut/upgrade! [(r/map->Dependency (assoc dep :file temp2 :name "bar" :cooldown-version nil))]
                                  {:force true :reporter report/no-output-reporter})))))))

(t/deftest upgrade!-unsupported-test
  (let [[temp1] temp-files
        dep (r/map->Dependency {:project ::unknown
                                :latest-version "LATEST"
                                :file temp1})]
    (t/is (= "before0" (slurp temp1)))

    (let [sw (java.io.StringWriter.)
          err-str (binding [*err* sw]
                    (sut/upgrade! [dep] {:force true})
                    (str sw))]
      (t/is (not= -1 (.indexOf err-str "Not supported"))))

    (t/is (= "before0" (slurp temp1)))))

(t/deftest upgrade!-confirm-test
  (let [[temp1] temp-files
        dep (r/map->Dependency {:project ::test
                                :latest-version "LATEST"
                                :file temp1})]
    (t/is (= "before0" (slurp temp1)))

    (t/testing "input no"
      (let [out-str (with-out-str
                      (with-redefs [read (constantly 'n)]
                        (sut/upgrade! [dep] {:force false})))]
        (t/is (not= -1 (.indexOf out-str "Do you want to upgrade"))))

      (t/is (= "before0" (slurp temp1))))

    (t/testing "input yes"
      (let [out-str (with-out-str
                      (with-redefs [read (constantly 'y)]
                        (sut/upgrade! [dep] {:force false})))]
        (t/is (not= -1 (.indexOf out-str "Do you want to upgrade"))))

      (t/is (= "afterLATEST" (slurp temp1))))))
