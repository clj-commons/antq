(ns antq.util.ver-test
  (:require
   [antq.record :as r]
   [antq.util.ver :as sut]
   [clojure.test :as t]))

(t/deftest remove-qualifiers-test
  (t/are [expected in] (= expected (sut/remove-qualifiers in))
    "1.0.0" "1.0.0-alpha"
    "1.0.0" "1.0.0-alpha2"
    "1.0.0" "1.0.0.alpha"
    "1.0.0" "1.0.0.alpha2"
    "1.0.0" "1.0.0-beta"
    "1.0.0" "1.0.0-milestone"
    "1.0.0" "1.0.0-rc"
    "1.0.0" "1.0.0-snapshot"
    "1.0.0" "1.0.0-final"
    "1.0.0" "1.0.0-stable"))

(t/deftest normalize-version-test
  (t/are [expected in] (= expected (sut/normalize-version in))
    "1.0.0" "v1.0.0"
    "1.0.0" "vm-1.0.0"
    "1.0.0" "1.0.0"))

(t/deftest sem-ver?-test
  (t/are [expected in] (= expected (sut/sem-ver? in))
    true "1.0.0"
    true "1.0"
    true "1"
    true "1.0.0.0.0.0"
    false "foo"
    false "0.0-1"
    false ""))

(t/deftest normalize-latest-version-test
  (t/is (= "foo"
           (sut/normalize-latest-version (r/map->Dependency {:type :java :latest-version "foo"}))))

  (t/is (= (deref #'sut/no-latest-version-error)
           (sut/normalize-latest-version (r/map->Dependency {:type :java :latest-version nil}))))


  (t/testing "git-sha"
    (t/is (= "4c484d08630a5711f5a04c4f7e23c5fb1dad6cf9"
             (sut/normalize-latest-version (r/map->Dependency {:type :git-sha
                                                               :version "faf211bae9ad9dd399af6efbeaa8b01930c0482f"
                                                               :latest-version "4c484d08630a5711f5a04c4f7e23c5fb1dad6cf9"}))))
    (t/is (= "4c484d0"
             (sut/normalize-latest-version (r/map->Dependency {:type :git-sha
                                                               :version "faf211b"
                                                               :latest-version "4c484d08630a5711f5a04c4f7e23c5fb1dad6cf9"}))))))

(t/deftest in-range?-test
  (t/is (true? (sut/in-range? "1.0.0" "1.0.0")))
  (t/is (true? (sut/in-range? "1.0.0+1" "1.0.0+1")))
  (t/is (true? (sut/in-range? "1.0.0?" "1.0.0?")))

  (t/is (false? (sut/in-range? "1.0.0" "10000")))
  (t/is (false? (sut/in-range? "1.0.1" "1.0.0")))
  (t/is (false? (sut/in-range? "1.0.0+1" "1.0.0+2")))
  (t/is (false? (sut/in-range? "1.0.0?" "1.0.?")))

  (t/testing ".x"
    (t/is (true? (sut/in-range? "1.0.x" "1.0.0")))
    (t/is (true? (sut/in-range? "1.x" "1.0.0")))
    (t/is (true? (sut/in-range? "1.x" "1.1.0")))
    (t/is (true? (sut/in-range? "2.x" "2.0.0")))

    (t/is (false? (sut/in-range? "1.0.x" "1.1.0")))
    (t/is (false? (sut/in-range? "1.x" "2.0.0")))
    (t/is (false? (sut/in-range? "2.x" "1.2.0"))))

  (t/testing "*"
    (t/is (true? (sut/in-range? "1.0.*" "1.0.0")))
    (t/is (true? (sut/in-range? "1.0*" "1.0.0")))
    (t/is (true? (sut/in-range? "1.0*" "1.00")))
    (t/is (true? (sut/in-range? "1.0*" "1.0")))
    (t/is (true? (sut/in-range? "*" "1.0.0")))
    (t/is (true? (sut/in-range? "*" "2.0.0")))

    (t/is (false? (sut/in-range? "1.0.*" "1.1.0")))
    (t/is (false? (sut/in-range? "1.0*" "1.1.0"))))

  (t/testing "combination"
    (t/is (true? (sut/in-range? "1.0.x-alpha*" "1.0.0-alpha1")))

    (t/is (false? (sut/in-range? "1.0.x-alpha*" "1.0.0-bata1")))
    (t/is (false? (sut/in-range? "1.0.x-alpha*" "1.1.0-alpha1")))))
