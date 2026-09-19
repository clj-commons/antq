(ns antq.dep.github-action.uses-test
  (:require
   [antq.constant.github-action :as const.gh-action]
   [antq.dep.github-action.uses :as sut]
   [antq.record :as r]
   [clojure.test :as t]))

(defn- git-tag-dependency
  [m]
  (r/map->Dependency (assoc m :type :github-tag)))

(defn- git-sha-dependency
  [m]
  (r/map->Dependency (assoc m :type :git-sha)))

(t/deftest detect-invalid-test
  (t/is (nil? (sut/detect nil)))
  (t/is (nil? (sut/detect {})))
  (t/is (nil? (sut/detect [:uses "bar/baz"]))))

(t/deftest detect-github-tag-test
  (t/is (= [(git-tag-dependency {:name "foo/bar" :version "v1.0.0"
                                 :extra {const.gh-action/type-key "uses"}})]
           (sut/detect [:uses "foo/bar@v1.0.0"])))
  (t/is (= [(git-tag-dependency {:name "bar/baz" :version "master"
                                 :extra {const.gh-action/type-key "uses"}})]
           (sut/detect [:uses "bar/baz@master"]))))

(t/deftest detect-git-sha-test
  (t/is (= [(git-sha-dependency {:name "git/sha"
                                 :version "8be09192b01d78912b03852f5d6141e8c48f4179"
                                 :extra {:url "https://github.com/git/sha.git"
                                         const.gh-action/type-key "uses"}})]
           (sut/detect [:uses "git/sha@8be09192b01d78912b03852f5d6141e8c48f4179"])))
  (t/is (= [(git-sha-dependency {:name "git/sha-short"
                                 :version "8be0919"
                                 :extra {:url "https://github.com/git/sha-short.git"
                                         const.gh-action/type-key "uses"}})]
           (sut/detect [:uses "git/sha-short@8be0919"]))))
