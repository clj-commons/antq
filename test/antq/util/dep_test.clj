(ns antq.util.dep-test
  (:require
   [antq.record :as r]
   [antq.test-helper :as h]
   [antq.util.dep :as sut]
   [antq.util.maven :as u.mvn]
   [clojure.java.io :as io]
   [clojure.test :as t]))

(t/deftest compare-deps-test
  (t/testing "positive"
    (let [aaa (h/test-dep {:file "aa" :name "bb"})
          bbb (h/test-dep {:file "aa" :name "xx"})
          ccc (h/test-dep {:file "xx" :name "bb"})
          ddd (h/test-dep {:file "xx" :name "xx"})]
      (t/are [expected-fn x y] (expected-fn (sut/compare-deps x y))
        zero? aaa aaa
        neg?  aaa bbb
        neg?  aaa ccc
        neg?  aaa ddd

        pos?  bbb aaa
        zero? bbb bbb
        neg?  bbb ccc
        neg?  bbb ddd

        pos?  ccc aaa
        pos?  ccc bbb
        zero? ccc ccc
        neg?  ccc ddd

        pos?  ddd aaa
        pos?  ddd bbb
        pos?  ddd ccc
        zero? ddd ddd)))

  (t/testing "negative"
    (t/is (zero? (sut/compare-deps {} {})))
    (t/is (zero? (sut/compare-deps nil nil)))))

(t/deftest relative-path-test
  (t/is (= "foo" (sut/relative-path (io/file "foo"))))
  (t/is (= "foo" (sut/relative-path (io/file "." "foo"))))
  (t/is (= "foo" (sut/relative-path (io/file "./foo"))))

  (t/is (= (.getPath (io/file "foo" "bar"))
           (sut/relative-path (io/file "." "foo" "bar"))))

  (t/is (= (.getPath (io/file "foo" "bar"))
           (sut/relative-path (io/file "./foo" "bar")))))

(t/deftest name-candidates-test
  (t/is (= #{'foo/core}
           (sut/name-candidates "foo/core")))
  (t/is (= #{'foo/foo 'foo}
           (sut/name-candidates "foo/foo")))
  (t/is (= #{}
           (sut/name-candidates ""))))

(t/deftest repository-opts-test
  (t/is (= {:repositories u.mvn/default-repos
            :snapshots? false}
           (sut/repository-opts (r/map->Dependency {:version "1.0.0"}))))
  (t/is (= {:repositories (assoc u.mvn/default-repos
                                 "foo" {:url "s3://foo"})
            :snapshots? true}
           (sut/repository-opts (r/map->Dependency {:repositories {"foo" {:url "s3p://foo"}}
                                                    :version "1.0.0-SNAPSHOT"}))))
  (t/is (= {:repositories u.mvn/default-repos
            :snapshots? true}
           (sut/repository-opts (r/map->Dependency {:version "1.0.0-20240101.120000-1"})))))

(t/deftest normalize-path-test
  (t/are [expected input] (= (h/os-path expected) (sut/normalize-path input))
    "foo/bar" "foo/bar"
    "foo/bar" "foo/./bar"
    "bar" "foo/../bar"
    "bar/baz" "foo/../bar/baz"
    "bar/baz" "foo/../bar/./baz"
    "../foo" "../foo"
    "../bar" "foo/../../bar"
    ".." ".."
    "." "."
    "" ""
    "foo" "foo"
    "foo" "./foo"))
