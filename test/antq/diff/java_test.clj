(ns antq.diff.java-test
  (:require
   [antq.diff :as diff]
   [antq.diff.java]
   [antq.record :as r]
   [antq.util.dep :as u.dep]
   [antq.util.git :as u.git]
   [antq.util.maven :as u.mvn]
   [clojure.java.io :as io]
   [clojure.test :as t]))

(t/deftest get-diff-url-test
  (let [dep (r/map->Dependency {:type :java
                                :name "foo/bar"
                                :version "1.0"
                                :latest-version "2.0"})]
    (t/testing "https://github.com"
      (with-redefs [u.dep/pom-file-with-timeout (constantly (io/file "pom.xml"))
                    u.mvn/read-pom (constantly {:scm-url "https://github.com/bar/baz"})
                    u.git/tags-by-ls-remote (fn [url]
                                              (when (= "https://github.com/bar/baz/" url)
                                                ["v0.0" "v1.0" "v2.0" "v3.0"]))]
        (t/is (= "https://github.com/bar/baz/compare/v1.0...v2.0"
                 (diff/get-diff-url dep)))))

    (t/testing "git@github.com"
      (with-redefs [u.dep/pom-file-with-timeout (constantly (io/file "pom.xml"))
                    u.mvn/read-pom (constantly {:scm-url "git@github.com:git/at"})
                    u.git/tags-by-ls-remote (fn [url]
                                              (when (= "https://github.com/git/at/" url)
                                                ["v0.0" "v1.0" "v2.0" "v3.0"]))]
        (t/is (= "https://github.com/git/at/compare/v1.0...v2.0"
                 (diff/get-diff-url (assoc dep :name "git/at"))))))

    (t/testing "POM file not found"
      (with-redefs [u.dep/pom-file-with-timeout (constantly nil)]
        (t/is (nil? (diff/get-diff-url (assoc dep :name "fetch/repo-url"))))))

    (t/testing "POM not found"
      (with-redefs [u.dep/pom-file-with-timeout (constantly (io/file "pom.xml"))
                    u.mvn/read-pom (fn [_] (throw (java.io.FileNotFoundException. "test exception")))]
        (t/is (nil? (diff/get-diff-url (assoc dep :name "pom/not-found"))))))

    (t/testing "POM does not have SCM"
      (with-redefs [u.dep/pom-file-with-timeout (constantly (io/file "pom.xml"))
                    u.mvn/read-pom (constantly {:url "https://github.com/pom/no-scm"})
                    u.git/tags-by-ls-remote (fn [url]
                                              (when (= "https://github.com/pom/no-scm/" url)
                                                ["v0.0" "v1.0" "v2.0" "v3.0"]))]
        (t/is (= "https://github.com/pom/no-scm/compare/v1.0...v2.0"
                 (diff/get-diff-url (assoc dep :name "pom/noscm"))))))

    (t/testing "not supported URL"
      (with-redefs [u.dep/pom-file-with-timeout (constantly (io/file "pom.xml"))
                    u.mvn/read-pom (constantly {:scm-url "https://not-supported.com"})]
        (t/is (nil? (diff/get-diff-url (assoc dep :name "not/supported"))))))))
