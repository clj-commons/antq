(ns antq.upgrade.github-action-test
  (:require
   [antq.constant.github-action :as const.gh-action]
   [antq.dep.github-action :as dep.gha]
   [antq.record :as r]
   [antq.test-helper :as h]
   [antq.test-report]
   [antq.upgrade :as upgrade]
   [antq.upgrade.github-action :as upgrade.gha]
   [antq.util.git :as u.git]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :as t]))

(def ^:private dummy-dep
  (r/map->Dependency {:project :github-action
                      :type :github-tag
                      :name "foo/bar"
                      :version "v1.0.0"
                      :latest-version "v9.0.0"
                      :file (io/resource "dep/test_github_action.yml")
                      :extra {const.gh-action/type-key "uses"}}))

(def ^:private dummy-pinned-dep
  (r/map->Dependency {:project :github-action
                      :type :git-tag-and-sha
                      :name "git/sha-with-version"
                      :version "v1.2.3"
                      :latest-version "v9.0.0"
                      :file (io/resource "dep/test_github_action.yml")
                      :extra {:url "https://github.com/git/sha-with-version.git"
                              :sha "8be09192b01d78912b03852f5d6141e8c48f4179"
                              const.gh-action/type-key "uses"}}))

(def ^:private dummy-not-supported-dep
  (r/map->Dependency {:project :github-action
                      :type :github-tag
                      :name "bar/baz"
                      :latest-version "v9.0.0"
                      :file (io/resource "dep/test_github_action.yml")}))

(def ^:private dummy-clj-kondo-dep
  (r/map->Dependency {:project :github-action
                      :type :java
                      :name "clj-kondo/clj-kondo"
                      :latest-version "9.0.0"
                      :file (io/resource "dep/test_github_action_third_party.yml")
                      :extra {const.gh-action/type-key "DeLaGuardo/setup-clj-kondo"}}))

(def ^:private dummy-graalvm-dep
  (r/map->Dependency {:project :github-action
                      :type :github-tag
                      :name "graalvm/graalvm-ce-builds"
                      :latest-version "9.0.0"
                      :file (io/resource "dep/test_github_action_third_party.yml")
                      :extra {const.gh-action/type-key "DeLaGuardo/setup-graalvm"}}))

(def ^:private dummy-cljstyle-dep
  (r/map->Dependency {:project :github-action
                      :type :github-tag
                      :name "greglook/cljstyle"
                      :latest-version "9.0.0"
                      :file (io/resource "dep/test_github_action_third_party.yml")
                      :extra {const.gh-action/type-key "0918nobita/setup-cljstyle"}}))

(t/deftest upgrade-dep-test
  (t/testing "supported"
    (let [from-deps (->> (:file dummy-dep)
                         (slurp)
                         (dep.gha/extract-deps ""))
          temp-content (->> dummy-dep
                            (upgrade/upgrader))
          to-deps (h/with-temp-file
                   [temp-file temp-content]
                   (->> (assoc dummy-dep
                               :version "v2.0.0"
                               :file temp-file)
                        (upgrade/upgrader)
                        (dep.gha/extract-deps "")))]
      (t/is (= #{{:name "foo/bar" :version {:- "v1.0.0" :+ "v9.0.0"}}
                 {:name "foo/bar" :version {:- "v2.0.0" :+ "v9.0.0"}}}
               (h/diff-deps from-deps to-deps)))))

  (t/testing "commit SHA with version comment"
    (with-redefs [u.git/tag-sha-by-ls-remote (constantly "0123456789abcdef0123456789abcdef01234567")]
      (let [from-deps (->> (:file dummy-pinned-dep)
                           (slurp)
                           (dep.gha/extract-deps ""))
            to-deps (->> dummy-pinned-dep
                         (upgrade/upgrader)
                         (dep.gha/extract-deps ""))]
        (t/is (= #{{:name "git/sha-with-version"
                    :version {:- "v1.2.3" :+ "v9.0.0"}
                    :url "https://github.com/git/sha-with-version.git"
                    :sha {:- "8be09192b01d78912b03852f5d6141e8c48f4179"
                          :+ "0123456789abcdef0123456789abcdef01234567"}}}
                 (h/diff-deps from-deps to-deps))))))

  (t/testing "not supported"
    (t/is (nil? (upgrade/upgrader dummy-not-supported-dep)))))

(t/deftest upgrade-third-party-dep-test
  (t/testing "clj-kondo"
    (let [from-deps (->> (:file dummy-clj-kondo-dep)
                         (slurp)
                         (dep.gha/extract-deps ""))
          to-deps (->> dummy-clj-kondo-dep
                       (upgrade/upgrader)
                       (dep.gha/extract-deps ""))]
      (t/is (= #{{:name "clj-kondo/clj-kondo" :version {:- "5" :+ "9.0.0"}}
                 {:name "clj-kondo/clj-kondo" :version {:- "-5" :+ "9.0.0"}}}
               (h/diff-deps from-deps to-deps)))))

  (t/testing "graalvm"
    (let [from-deps (->> (:file dummy-graalvm-dep)
                         (slurp)
                         (dep.gha/extract-deps ""))
          to-deps (->> dummy-graalvm-dep
                       (upgrade/upgrader)
                       (dep.gha/extract-deps ""))]
      (t/is (= #{{:name "graalvm/graalvm-ce-builds" :version {:- "6" :+ "9.0.0"}}
                 {:name "graalvm/graalvm-ce-builds" :version {:- "-6" :+ "9.0.0"}}}
               (h/diff-deps from-deps to-deps)))))

  (t/testing "cljstyle"
    (let [from-deps (->> (:file dummy-cljstyle-dep)
                         (slurp)
                         (dep.gha/extract-deps ""))
          to-deps (->> dummy-cljstyle-dep
                       (upgrade/upgrader)
                       (dep.gha/extract-deps ""))]
      (t/is (= #{{:name "greglook/cljstyle" :version {:- "7" :+ "9.0.0"}}
                 {:name "greglook/cljstyle" :version {:- "-7" :+ "9.0.0"}}}
               (h/diff-deps from-deps to-deps))))))

(t/deftest pin!-test
  (let [content (slurp (io/resource "dep/test_github_action.yml"))
        release-shas {["https://github.com/foo/bar.git" "v1.0.0"] "1111111111111111111111111111111111111111"}]
    (h/with-temp-file
     [temp-file content]
     (with-redefs [u.git/tag-sha-by-ls-remote (fn [url tag] (get release-shas [url tag]))]
       (upgrade.gha/pin! (dep.gha/extract-deps (str temp-file) content)))
     (t/is (= #{{:- "      - uses: foo/bar@v1.0.0"
                 :+ "      - uses: foo/bar@1111111111111111111111111111111111111111 # v1.0.0"}}
              (h/diff-lines (str/split-lines content)
                            (str/split-lines (slurp temp-file))))))))
