(ns antq.upgrade.clojure-test
  (:require
   [antq.dep.clojure :as dep.clj]
   [antq.record :as r]
   [antq.test-helper :as h]
   [antq.upgrade :as upgrade]
   [antq.upgrade.clojure]
   [antq.util.git :as u.git]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.test :as t]))

(def ^:private dummy-java-dep
  (r/map->Dependency {:project :clojure
                      :type :java
                      :name "foo/core"
                      :latest-version "9.0.0"
                      :file (io/resource "dep/test_deps.edn")}))

(def ^:private dummy-git-dep
  (r/map->Dependency {:project :clojure
                      :type :git-sha
                      :name "sha/sha"
                      :latest-version "new-sha"
                      :file (io/resource "dep/test_deps.edn")}))

(def ^:private dummy-git-git-sha-dep
  (r/map->Dependency {:project :clojure
                      :type :git-sha
                      :name "git-sha/git-sha"
                      :latest-version "new-sha"
                      :file (io/resource "dep/test_deps.edn")}))

(def ^:private dummy-git-tag-short-sha-dep
  (r/map->Dependency {:project :clojure
                      :type :git-tag-and-sha
                      :name "tag-short-sha/tag-short-sha"
                      :latest-version "v9.9.9"
                      :file (io/resource "dep/test_deps.edn")
                      :extra {:sha "1234567"}}))

(def ^:private dummy-git-tag-long-sha-dep
  (r/map->Dependency {:project :clojure
                      :type :git-tag-and-sha
                      :name "git-tag-long-sha/git-tag-long-sha"
                      :latest-version "v9.9.9"
                      :file (io/resource "dep/test_deps.edn")
                      :extra {:sha "123456789x123456789x123456789x123456789x"}}))

(def ^:private dummy-short-meta-dep
  (r/map->Dependency {:project :clojure
                      :type :java
                      :name "short-meta"
                      :latest-version "9.0.0"
                      :file (io/resource "dep/test_deps.edn")}))

(def ^:private dummy-full-meta-dep
  (r/map->Dependency {:project :clojure
                      :type :java
                      :name "full-meta"
                      :latest-version "9.0.0"
                      :file (io/resource "dep/test_deps.edn")}))

(def ^:private dummy-no-version-dep
  (r/map->Dependency {:project :clojure
                      :type :java
                      :name "no-version"
                      :latest-version "9.9.9"
                      :file (io/resource "dep/test_deps.edn")}))

(def ^:private dummy-namespaced-mvn-dep
  (r/map->Dependency {:project :clojure
                      :type :java
                      :name "namespaced/mvn"
                      :latest-version "9.0.0"
                      :file (io/resource "dep/test_deps.edn")}))

(def ^:private dummy-namespaced-sha-dep
  (r/map->Dependency {:project :clojure
                      :type :git-sha
                      :name "namespaced/sha"
                      :latest-version "new-sha"
                      :file (io/resource "dep/test_deps.edn")}))

(def ^:private dummy-namespaced-tag-and-sha-dep
  (r/map->Dependency {:project :clojure
                      :type :git-tag-and-sha
                      :name "namespaced/tag-and-sha"
                      :latest-version "v9.9.9"
                      :file (io/resource "dep/test_deps.edn")
                      :extra {:sha "123abcd"}}))

(defn- select-deps
  [name-pred file]
  (->> (slurp file)
       (dep.clj/extract-deps "")
       (filter #(name-pred (:name %)))
       (map #(assoc % :file file))))

(t/deftest upgrade-dep-test
  (t/testing "java"
    (let [from-deps (->> dummy-java-dep
                         :file
                         (slurp)
                         (dep.clj/extract-deps ""))
          upgraded (upgrade/upgrader dummy-java-dep)
          upgraded-edn (edn/read-string upgraded)
          to-deps (dep.clj/extract-deps "" upgraded)]
      (t/is (= #{{:name "foo/core" :version {:- "1.0.0" :+ "9.0.0"}}
                 {:name "foo/core" :version {:- "1.1.0" :+ "9.0.0"}}}
               (h/diff-deps from-deps to-deps)))

      (t/testing "same name deps should be upgraded"
        (t/is (= '{foo/core {:mvn/version "9.0.0"}}
                 (get-in upgraded-edn [:aliases :same-name :extra-deps]))))

      (t/testing "excluded deps should not be upgraded"
        (t/is (= '{foo/core {:mvn/version "0.0.1"}}
                 (get-in upgraded-edn [:aliases :same-name-but-excluded :extra-deps]))))))


  (t/testing "git :sha"
    (let [from-deps (->> dummy-git-dep
                         :file
                         (slurp)
                         (dep.clj/extract-deps ""))
          to-deps (->> dummy-git-dep
                       (upgrade/upgrader)
                       (dep.clj/extract-deps ""))]
      (t/is (= #{{:name "sha/sha"
                  :version {:- "dummy-sha" :+ "new-sha"}
                  :url "https://github.com/example/sha.git"}}
               (h/diff-deps from-deps to-deps)))))

  (t/testing "git :git/sha"
    (let [from-deps (->> dummy-git-git-sha-dep
                         :file
                         (slurp)
                         (dep.clj/extract-deps ""))
          to-deps (->> dummy-git-git-sha-dep
                       (upgrade/upgrader)
                       (dep.clj/extract-deps ""))]
      (t/is (= #{{:name "git-sha/git-sha"
                  :version {:- "dummy-git-sha" :+ "new-sha"}
                  :url "https://github.com/example/git-sha.git"}}
               (h/diff-deps from-deps to-deps)))))

  (t/testing "git :tag (short sha)"
    (with-redefs [u.git/tag-sha-by-ls-remote (constantly "9876543210abcdefghijklmnopqrstuvwxyz1234")]
      (let [from-deps (->> dummy-git-tag-short-sha-dep
                           :file
                           (slurp)
                           (dep.clj/extract-deps ""))
            to-deps (->> dummy-git-tag-short-sha-dep
                         (upgrade/upgrader)
                         (dep.clj/extract-deps ""))]
        (t/is (= #{{:name "tag-short-sha/tag-short-sha"
                    :version {:- "v1.2.3" :+ "v9.9.9"}
                    :url "https://github.com/example/tag-short.git"
                    :sha {:- "123abcd" :+ "9876543"}}}
                 (h/diff-deps from-deps to-deps))))))

  (t/testing "git :git/tag (long sha)"
    (with-redefs [u.git/tag-sha-by-ls-remote (constantly "9876543210abcdefghijklmnopqrstuvwxyz1234")]
      (let [from-deps (->> dummy-git-tag-long-sha-dep
                           :file
                           (slurp)
                           (dep.clj/extract-deps ""))
            to-deps (->> dummy-git-tag-long-sha-dep
                         (upgrade/upgrader)
                         (dep.clj/extract-deps ""))]
        (t/is (= #{{:name "git-tag-long-sha/git-tag-long-sha"
                    :version {:- "v2.3.4" :+ "v9.9.9"}
                    :url "https://github.com/example/git-tag-long.git"
                    :sha {:- "1234567890abcdefghijklmnopqrstuvwxyz1234"
                          :+ "9876543210abcdefghijklmnopqrstuvwxyz1234"}}}
                 (h/diff-deps from-deps to-deps))))))

  (t/testing "short meta"
    (let [from-deps (->> dummy-short-meta-dep
                         :file
                         (slurp)
                         (dep.clj/extract-deps ""))
          to-deps (->> dummy-short-meta-dep
                       (upgrade/upgrader)
                       (dep.clj/extract-deps ""))]
      (t/is (= #{{:name "short-meta/short-meta" :version {:- "2.5.8" :+ "9.0.0"}}}
               (h/diff-deps from-deps to-deps)))))

  (t/testing "full meta"
    (let [from-deps (->> dummy-full-meta-dep
                         :file
                         (slurp)
                         (dep.clj/extract-deps ""))
          to-deps (->> dummy-full-meta-dep
                       (upgrade/upgrader)
                       (dep.clj/extract-deps ""))]
      (t/is (= #{{:name "full-meta/full-meta" :version {:- "2.6.9" :+ "9.0.0"}}}
               (h/diff-deps from-deps to-deps)))))

  (t/testing "namespaced mvn"
    (let [from-deps (->> dummy-namespaced-mvn-dep
                         :file
                         (slurp)
                         (dep.clj/extract-deps ""))
          to-deps (->> dummy-namespaced-mvn-dep
                       (upgrade/upgrader)
                       (dep.clj/extract-deps ""))]
      (t/is (= #{{:name "namespaced/mvn" :version {:- "1.0.0" :+ "9.0.0"}}}
               (h/diff-deps from-deps to-deps)))))

  (t/testing "namespaced git-sha"
    (let [from-deps (->> dummy-namespaced-sha-dep
                         :file
                         (slurp)
                         (dep.clj/extract-deps ""))
          to-deps (->> dummy-namespaced-sha-dep
                       (upgrade/upgrader)
                       (dep.clj/extract-deps ""))]
      (t/is (= #{{:name "namespaced/sha"
                  :url "https://github.com/example/git-sha.git"
                  :version {:- "1234567890abcdefghijklmnopqrstuvwxyz1234"
                            :+ "new-sha"}}}
               (h/diff-deps from-deps to-deps)))))

  (t/testing "namespaced git-tag-and-sha"
    (with-redefs [u.git/tag-sha-by-ls-remote (constantly "9876543210abcdefghijklmnopqrstuvwxyz1234")]
      (let [from-deps (->> dummy-namespaced-tag-and-sha-dep
                           :file
                           (slurp)
                           (dep.clj/extract-deps ""))
            to-deps (->> dummy-namespaced-tag-and-sha-dep
                         (upgrade/upgrader)
                         (dep.clj/extract-deps ""))]
        (t/is (= #{{:name "namespaced/tag-and-sha"
                    :url "https://github.com/example/tag-short.git"
                    :version {:- "v1.2.3" :+ "v9.9.9"}
                    :sha {:- "123abcd" :+ "9876543"}}}
                 (h/diff-deps from-deps to-deps))))))

  (t/testing "no corresponding value"
    (let [from-deps (->> dummy-no-version-dep
                         :file
                         (slurp)
                         (dep.clj/extract-deps ""))
          to-deps (->> dummy-no-version-dep
                       (upgrade/upgrader)
                       (dep.clj/extract-deps ""))]
      (t/is (empty? (h/diff-deps from-deps to-deps))))))

(t/deftest upgrade-dep-replce-deps-test
  (let [dummy-dep (assoc dummy-java-dep :name "rep")
        from-deps (->> dummy-dep
                       :file
                       (slurp)
                       (dep.clj/extract-deps ""))
        to-deps (->> dummy-dep
                     (upgrade/upgrader)
                     (dep.clj/extract-deps ""))]
    (t/is (= #{{:name "rep/rep" :version {:- "4.0.0" :+ "9.0.0"}}}
             (h/diff-deps from-deps to-deps)))))

(t/deftest upgrade-dep-override-deps-test
  (let [dummy-dep (assoc dummy-java-dep :name "ovr")
        from-deps (->> dummy-dep
                       :file
                       (slurp)
                       (dep.clj/extract-deps ""))
        to-deps (->> dummy-dep
                     (upgrade/upgrader)
                     (dep.clj/extract-deps ""))]
    (t/is (= #{{:name "ovr/ovr" :version {:- "5.0.0" :+ "9.0.0"}}}
             (h/diff-deps from-deps to-deps)))))

(t/deftest upgrade-dep-default-deps-test
  (let [dummy-dep (assoc dummy-java-dep :name "dft")
        from-deps (->> dummy-dep
                       :file
                       (slurp)
                       (dep.clj/extract-deps ""))
        to-deps (->> dummy-dep
                     (upgrade/upgrader)
                     (dep.clj/extract-deps ""))]
    (t/is (= #{{:name "dft/dft" :version {:- "6.0.0" :+ "9.0.0"}}}
             (h/diff-deps from-deps to-deps)))))

(t/deftest upgrade-dep-namespaced-java-deps-test
  (let [from-deps (->> (io/resource "dep/test_deps_namespaced_map.edn")
                       (select-deps #{"foo/bar"}))
        to-deps (->> (assoc (first from-deps) :latest-version "9.0.0")
                     (upgrade/upgrader)
                     (dep.clj/extract-deps ""))]
    (t/is (= #{{:name "foo/bar" :version {:- "1.0.0" :+ "9.0.0"}}}
             (h/diff-deps from-deps to-deps)))))
