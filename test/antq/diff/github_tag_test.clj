(ns antq.diff.github-tag-test
  (:require
   [antq.diff :as diff]
   [antq.diff.github-tag]
   [antq.record :as r]
   [antq.test-report]
   [antq.util.git :as u.git]
   [clojure.test :as t]))

(t/deftest get-diff-url-test
  (let [dep (r/map->Dependency {:type :github-tag
                                :name "foo/bar"
                                :version "1.0"
                                :latest-version "2.0"})]
    (with-redefs [u.git/tags-by-ls-remote (fn [url]
                                            (when (= "https://github.com/foo/bar" url)
                                              ["v0.0" "v1.0" "v2.0" "v3.0"]))]
      (t/is (= "https://github.com/foo/bar/compare/v1.0...v2.0"
               (diff/get-diff-url dep))))))
