(ns antq.util.url-test
  (:require
   [antq.util.url :as sut]
   [clojure.test :as t]))

(t/deftest ensure-tail-slash-test
  (t/is (= "foo/" (sut/ensure-tail-slash "foo")))
  (t/is (= "foo/" (sut/ensure-tail-slash "foo/"))))

(t/deftest ensure-git-https-url-test
  (t/is (= "https://github.com/foo/bar/"
           (sut/ensure-git-https-url "https://github.com/foo/bar")))
  (t/is (= "https://github.com/foo/bar/"
           (sut/ensure-git-https-url "https://github.com/foo/bar.git")))
  (t/is (= "https://github.com/foo/bar/"
           (sut/ensure-git-https-url "git@github.com:foo/bar")))
  (t/is (= "https://github.com/foo/bar/"
           (sut/ensure-git-https-url "git@github.com/foo/bar.git"))))

(t/deftest ensure-https
  (t/is (= "https://github.com"
           (sut/ensure-https "https://github.com")))
  (t/is (= "https://github.com"
           (sut/ensure-https "http://github.com")))
  (t/is (= "git@github.com"
           (sut/ensure-https "git@github.com")))
  (t/is (= ""
           (sut/ensure-https ""))))
