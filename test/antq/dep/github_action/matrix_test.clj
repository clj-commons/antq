(ns antq.dep.github-action.matrix-test
  (:require
   [antq.dep.github-action.matrix :as sut]
   [antq.record :as r]
   [antq.test-report]
   [antq.util.dep :as u.dep]
   [clojure.test :as t]))

;; dummy multimethod to confirm u.dep/normalize-by-name
(defmethod u.dep/normalize-version-by-name "foo"
  [dep]
  (update dep :version #(if (= "2" %) "two" %)))

(def ^:private dummy-parsed-yaml
  {:jobs
   {:job1 {:strategy {:matrix {:foo ["1" "2"]}}}
    :job2 {:strategy {:matrix {:foo ["3" "4"]}}}}})

(def ^:private dummy-deps
  [(r/map->Dependency {:name "foo" :version "${{matrix.foo}}"})
   (r/map->Dependency {:name "bar" :version "9"})
   (r/map->Dependency {:name "baz" :version 10})])

(t/deftest expand-matrixed-value-test
  (t/is (= #{(r/map->Dependency {:name "foo" :version "1" :only-newest-version? true})
             (r/map->Dependency {:name "foo" :version "two" :only-newest-version? true})
             (r/map->Dependency {:name "bar" :version "9"})
             (r/map->Dependency {:name "baz" :version 10})}
           (set (sut/expand-matrix-value
                 dummy-parsed-yaml
                 :job1
                 dummy-deps))))
  (t/is (= #{(r/map->Dependency {:name "foo" :version "3" :only-newest-version? true})
             (r/map->Dependency {:name "foo" :version "4" :only-newest-version? true})
             (r/map->Dependency {:name "bar" :version "9"})
             (r/map->Dependency {:name "baz" :version 10})}
           (set (sut/expand-matrix-value
                 dummy-parsed-yaml
                 :job2
                 dummy-deps)))))
