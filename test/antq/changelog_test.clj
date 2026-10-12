(ns antq.changelog-test
  (:require
   [antq.changelog :as sut]
   [antq.record :as r]
   [antq.test-report]
   [antq.util.dep :as u.dep]
   [antq.util.git :as u.git]
   [clojure.test :as t]))

(t/deftest get-changelog-url-test
  (t/testing "java"
    (let [dep (r/map->Dependency {:type :java
                                  :name "foo/java"
                                  :version "1.0"
                                  :latest-version "2.0"})]
      (with-redefs [u.dep/get-scm-url (constantly "https://github.com/foo/java")
                    u.git/tags-by-ls-remote (constantly ["v2.0"])
                    sut/get-root-file-names (constantly ["CHANGELOG.md"])]
        (t/is (= "https://github.com/foo/java/blob/v2.0/CHANGELOG.md"
                 (sut/get-changelog-url dep))))))

  (t/testing "git-sha"
    (let [dep (r/map->Dependency {:type :git-sha
                                  :name "foo/git-sha"
                                  :extra {:url "https://github.com/foo/git-sha"}
                                  :version "1.0"
                                  :latest-version "2.0"})]
      (with-redefs [sut/get-root-file-names (constantly ["CHANGELOG.adoc"])
                    u.git/tags-by-ls-remote (constantly ["v2.0" "2.0"])]
        (t/is (= "https://github.com/foo/git-sha/blob/2.0/CHANGELOG.adoc"
                 (sut/get-changelog-url dep))))))

  (t/testing "git-tag-and-sha"
    (let [dep (r/map->Dependency {:type :git-tag-and-sha
                                  :name "foo/git-sha"
                                  :extra {:url "https://github.com/foo/git-tag-and-sha"}
                                  :version "1.0"
                                  :latest-version "2.0"})]
      (with-redefs [sut/get-root-file-names (constantly ["CHANGELOG.adoc"])
                    u.git/tags-by-ls-remote (constantly ["v2.0" "2.0"])]
        (t/is (= "https://github.com/foo/git-tag-and-sha/blob/2.0/CHANGELOG.adoc"
                 (sut/get-changelog-url dep))))))

  (t/testing "github-tag"
    (let [dep (r/map->Dependency {:type :github-tag
                                  :name "foo/github-tag"
                                  :version "1.0"
                                  :latest-version "2.0"})]
      (with-redefs [sut/get-root-file-names (constantly ["CHANGELOG.org"])
                    u.git/tags-by-ls-remote (constantly ["2.0"])]
        (t/is (= "https://github.com/foo/github-tag/blob/2.0/CHANGELOG.org"
                 (sut/get-changelog-url dep)))))))

(t/deftest get-changelog-url-not-supported-test
  (let [dep (r/map->Dependency {:type :git-sha
                                :name "foo/git-sha"
                                :extra {:url "https://gitlab.com/not/supported"}
                                :version "1.0"
                                :latest-version "2.0"})]
    (t/is (nil? (sut/get-changelog-url dep)))))
