(ns antq.download-test
  (:require
   [antq.download :as sut]
   [antq.test-report]
   [antq.util.git :as u.git]
   [clojure.test :as t]
   [clojure.tools.deps :as deps]))

(defn- test-download!
  [m]
  (-> m
      (sut/download!)
      (dissoc :mvn/repos)))

(t/deftest download!-test
  (with-redefs [deps/resolve-deps (fn [deps-map _args-map] deps-map)]
    (t/testing "java"
      (t/is (= {:deps {'foo/bar {:mvn/version "1.0.0"}}}
               (test-download! [{:type :java
                                 :name 'foo/bar
                                 :latest-version "1.0.0"}]))))

    (t/testing "git-sha"
      (t/is (= {:deps {'foo/bar {:git/url "https://example.com"
                                 :git/sha "SHA"}}}
               (test-download! [{:type :git-sha
                                 :name 'foo/bar
                                 :latest-version "SHA"
                                 :extra {:url "https://example.com"}}]))))

    (t/testing "git-tag-and-sha"
      (with-redefs [u.git/tag-sha-by-ls-remote
                    (fn [url tag]
                      (when (and (= "https://example.com" url)
                                 (= "v1.0.0" tag))
                        "SHA2"))]
        (t/is (= {:deps {'foo/bar {:git/url "https://example.com"
                                   :git/tag "v1.0.0"
                                   :git/sha "SHA2"}}}
                 (test-download! [{:type :git-tag-and-sha
                                   :name 'foo/bar
                                   :latest-version "v1.0.0"
                                   :extra {:url "https://example.com"}}])))))

    (t/testing "else"
      (t/is (= {:deps nil}
               (test-download! [{:type :invalid}]))))))
