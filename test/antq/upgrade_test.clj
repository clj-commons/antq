(ns antq.upgrade-test
  (:require
   [antq.record :as r]
   [antq.test-report]
   [antq.upgrade :as sut]
   [antq.util.exception :as u.ex]
   [clojure.java.io :as io]
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
