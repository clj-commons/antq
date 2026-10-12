(ns antq.diff.git-sha-test
  (:require
   [antq.diff :as diff]
   [antq.diff.git-sha]
   [antq.record :as r]
   [antq.test-report]
   [clojure.test :as t]))

(t/deftest get-diff-url-test
  (let [dep (r/map->Dependency {:type :git-sha
                                :extra {:url "https://github.com/foo/bar"}
                                :version "1.0"
                                :latest-version "2.0"})]
    (t/is (= "https://github.com/foo/bar/compare/1.0...2.0"
             (diff/get-diff-url dep)))

    (t/testing "missing extra"
      (t/is (nil? (diff/get-diff-url (dissoc dep :extra)))))

    (t/testing "URL ending with .git"
      (t/is (= "https://github.com/bar/baz/compare/1.0...2.0"
               (diff/get-diff-url (assoc-in dep [:extra :url] "https://github.com/bar/baz.git")))))

    (t/testing "not supported extra URL"
      (t/is (nil? (diff/get-diff-url (assoc-in dep [:extra :url] "INVALID")))))))
