(ns antq.api-test
  (:require
   [antq.api :as sut]
   [antq.core :as core]
   [antq.record :as r]
   [antq.report :as report]
   [antq.test-report]
   [antq.upgrade :as upgrade]
   [antq.util.file :as u.file]
   [clojure.test :as t]))

(t/deftest outdated-deps-test
  (let [test-deps-map '{com.github.liquidz/test {:mvn/version "1.0.0"}}
        test-repositories {"test" {:url "https://example.com"}}
        expected-deps [(r/map->Dependency {:type :java
                                           :project :clojure
                                           :file ""
                                           :name "com.github.liquidz/test"
                                           :version "1.0.0"})]]
    (with-redefs [core/antq (fn [& args] args)]
      (t/is (= [{:reporter report/no-output-reporter}
                expected-deps]
               (sut/outdated-deps test-deps-map))))

    (t/testing "CLI options"
      (with-redefs [core/antq (fn [& args] args)]
        (t/is (= [{:reporter report/no-output-reporter
                   :no-changes true}
                  expected-deps]
                 (sut/outdated-deps test-deps-map {:no-changes true})))))

    (t/testing "repositories"
      (with-redefs [core/antq (fn [& args] args)]
        (t/is (= [{:reporter report/no-output-reporter}
                  (map #(assoc % :repositories test-repositories)
                       expected-deps)]
                 (sut/outdated-deps test-deps-map {:repositories test-repositories})))))))

(t/deftest upgrade-deps!-test
  (let [test-dep (r/map->Dependency {:type :java
                                     :project :clojure
                                     :file ""
                                     :name "com.github.liquidz/test"
                                     :version "1.0.0"
                                     :latest-version "2.0.0"})]
    (with-redefs [upgrade/upgrade! (fn [& args] args)
                  u.file/detect-project (constantly ::test)]
      (t/is (= [[(assoc test-dep
                        :project ::test
                        :file "dummy.txt")]
                {:force true :reporter report/no-output-reporter}]
               (sut/upgrade-deps! [{:file "dummy.txt"
                                    :dependency test-dep}]))))))
