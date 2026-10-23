(ns antq.dep.github-action.uses-test
  (:require
   [antq.constant.github-action :as const.gh-action]
   [antq.dep.github-action.uses :as sut]
   [antq.record :as r]
   [antq.test-report]
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
           (sut/detect [:uses "git/sha-short@8be0919"])))
  (t/is (= [(git-sha-dependency {:name "git/sha/sub-action"
                                 :version "8be09192b01d78912b03852f5d6141e8c48f4179"
                                 :extra {:url "https://github.com/git/sha.git"
                                         const.gh-action/type-key "uses"}})]
           (sut/detect [:uses "git/sha/sub-action@8be09192b01d78912b03852f5d6141e8c48f4179"]))))

(t/deftest with-version-comment-test
  (let [sha "8be09192b01d78912b03852f5d6141e8c48f4179"
        dep (first (sut/detect [:uses (str "git/sha@" sha)]))]
    (t/testing "a version comment pins the action to that release"
      (t/is (= (r/map->Dependency {:type :git-tag-and-sha
                                   :name "git/sha"
                                   :version "v1.2.3"
                                   :extra {:url "https://github.com/git/sha.git"
                                           :sha sha
                                           const.gh-action/type-key "uses"}})
               (sut/with-version-comment dep (str "  - uses: git/sha@" sha " # v1.2.3\n"))))
      (t/is (= "1.2"
               (:version (sut/with-version-comment dep (str "  uses: git/sha@" sha "  #1.2")))))
      (t/is (= "v2.0.0-rc.1"
               (:version (sut/with-version-comment dep (str "  - uses: git/sha@" sha " # v2.0.0-rc.1\n"))))))

    (t/testing "other comments leave the action following its default branch"
      (t/is (= dep (sut/with-version-comment dep (str "  - uses: git/sha@" sha "\n"))))
      (t/is (= dep (sut/with-version-comment dep (str "  - uses: git/sha@" sha " # main\n")))))

    (t/testing "an action pinned to a tag stays as it is"
      (let [tag-dep (first (sut/detect [:uses "foo/bar@v1.0.0"]))]
        (t/is (= tag-dep (sut/with-version-comment tag-dep "  - uses: foo/bar@v1.0.0 # v1.0.0\n")))))))
