(ns antq.core-test
  (:require
   [antq.changelog :as changelog]
   [antq.core :as sut]
   [antq.record :as r]
   [antq.util.dep :as u.dep]
   [antq.util.exception :as u.ex]
   [antq.util.git :as u.git]
   [antq.ver :as ver]
   [clojure.string :as str]
   [clojure.test :as t]))

(defmethod ver/get-sorted-versions :test
  [_ _]
  ["3.0.0" "2.0.0" "1.0.0"])

(t/deftest skip-artifacts?-test
  (t/testing "default"
    (t/are [expected in] (= expected (sut/skip-artifacts? (r/map->Dependency {:name in})
                                                          {}))
      false "org.clojure/clojure"
      false "org.clojure/foo"
      false "foo/clojure"
      false "foo"
      false "foo/bar"))

  (t/testing "custom: exclude"
    (t/are [expected in] (= expected (sut/skip-artifacts? (r/map->Dependency {:name in})
                                                          {:exclude ["org.clojure/clojure" "org.clojure/foo" "foo"]}))
      true "org.clojure/clojure"
      true "org.clojure/foo"
      false "foo/clojure"
      true "foo"
      false "foo/bar"))

  (t/testing "custom: focus"
    (t/are [expected in] (= expected (sut/skip-artifacts? (r/map->Dependency {:name in})
                                                          {:focus ["org.clojure/clojure" "foo"]}))
      false "org.clojure/clojure"
      true "org.clojure/foo"
      true "foo/clojure"
      false "foo"
      true "foo/bar"))

  (t/testing "focus works with specified version `@`"
    (t/are [expected in] (= expected (sut/skip-artifacts? (r/map->Dependency {:name in})
                                                          {:focus ["org.clojure/clojure" "foo@2.0.0"]}))
      false "org.clojure/clojure"
      true "org.clojure/foo"
      true "foo/clojure"
      false "foo"
      true "foo/bar"))
  (t/testing "`focus` shoud be prefer than `exclude`"
    (t/is (false? (sut/skip-artifacts? (r/map->Dependency {:name "org.clojure/clojure"})
                                       {:exclude ["org.clojure/clojure"]
                                        :focus ["org.clojure/clojure"]})))))

(t/deftest remove-skipping-versions-test
  (let [dep (r/map->Dependency {:name "foo"})]
    (t/testing "there are no target to remove"
      (t/is (= ["1" "2" "3"]
               (sut/remove-skipping-versions ["1" "2" "3"] dep {})))
      (t/is (= ["1" "2" "3"]
               (sut/remove-skipping-versions ["1" "2" "3"] dep {:exclude ["foo@4"]}))))

    (t/testing "only dep's name is matched"
      (t/is (= ["1" "2" "3"]
               (sut/remove-skipping-versions ["1" "2" "3"] dep {:exclude ["foo"]}))))

    (t/testing "only version number is matched"
      (t/is (= ["1" "2" "3"]
               (sut/remove-skipping-versions ["1" "2" "3"] dep {:exclude ["bar@2"]}))))

    (t/testing "dep's name and version number are matched"
      (t/is (= ["1" "3"]
               (sut/remove-skipping-versions ["1" "2" "3"] dep {:exclude ["foo@2"]})))
      (t/is (= ["1"]
               (sut/remove-skipping-versions ["1" "2" "3"] dep {:exclude ["foo@2"
                                                                          "foo@3"]})))
      (t/is (= []
               (sut/remove-skipping-versions ["1" "2" "3"] dep {:exclude ["foo@1"
                                                                          "foo@2"
                                                                          "foo@3"]}))))

    (t/testing "version range"
      (let [vers ["1.0.0" "1.0.1" "1.1.0" "1.1.1" "1.2.0" "2.0.0"]]
        (t/is (= ["2.0.0"]
                 (sut/remove-skipping-versions vers dep {:exclude ["foo@1.x"]})))
        (t/is (= ["1.1.0" "1.1.1" "1.2.0" "2.0.0"]
                 (sut/remove-skipping-versions vers dep {:exclude ["foo@1.0.x"]})))
        (t/is (= ["1.0.0" "1.0.1" "1.1.0" "1.1.1"]
                 (sut/remove-skipping-versions vers dep {:exclude ["foo@1.2.x"
                                                                   "foo@2.x"]})))
        (t/is (= ["1.0.0" "1.0.1" "1.1.0" "1.1.1" "1.2.0"]
                 (sut/remove-skipping-versions vers dep {:exclude ["foo@2.x"]})))
        (t/is (= ["1.0.0" "1.0.1" "1.1.0" "1.1.1" "1.2.0" "2.0.0"]
                 (sut/remove-skipping-versions vers dep {:exclude ["foo@9.x"]})))))))

(t/deftest using-release-version?-test
  (t/are [expected in] (= expected (sut/using-release-version?
                                    (r/map->Dependency {:version in})))
    true "RELEASE"
    true "master"
    true "main"
    true "latest"
    false "1.0.0"
    false ""))

(defn- test-dep
  [m]
  (r/map->Dependency (merge {:type :test} m)))

(t/deftest outdated-deps-test
  (let [deps [(test-dep {:name "alice" :version "1.0.0"})
              (test-dep {:name "bob" :version "2.0.0"})
              (test-dep {:name "charlie" :version "3.0.0"})]]

    (t/is (= [(test-dep {:name "alice" :version "1.0.0" :latest-version "3.0.0"})
              (test-dep {:name "bob" :version "2.0.0" :latest-version "3.0.0"})]
             (sut/outdated-deps deps {})))

    (t/testing "alice@3.0.0 should be excluded"
      (t/is (= [(test-dep {:name "alice" :version "1.0.0" :latest-version "2.0.0"})
                (test-dep {:name "bob" :version "2.0.0" :latest-version "3.0.0"})]
               (sut/outdated-deps deps {:exclude ["alice@3.0.0"]}))))
    (t/testing "alice is focused so only this dep should be kept"
      (t/is (= [(test-dep {:name "alice" :version "1.0.0" :latest-version "3.0.0"})]
               (sut/outdated-deps deps {:focus ["alice"]}))))
    (t/testing "focus containing specific version, should force it (0.5.0) even when newer exists (3.0.0)"
      (t/is (= [(test-dep {:name "alice" :version "1.0.0" :latest-version "0.5.0" :forced-version "0.5.0"})]
               (sut/outdated-deps deps {:focus ["alice@0.5.0"]}))))))

(t/deftest assoc-changes-url-test
  (let [dummy-dep {:type :java :name "foo/bar" :version "1" :latest-version "2"}]
    (t/testing "changelog"
      (with-redefs [u.dep/get-scm-url (constantly "https://github.com/foo/bar")
                    u.git/tags-by-ls-remote (constantly ["v1" "v2"])
                    changelog/get-root-file-names (constantly ["CHANGELOG.md"])]
        (t/is (= (assoc dummy-dep :changes-url "https://github.com/foo/bar/blob/v2/CHANGELOG.md")
                 (sut/assoc-changes-url dummy-dep)))

        (t/is (= (assoc dummy-dep :type :test)
                 (sut/assoc-changes-url (assoc dummy-dep :type :test))))))

    (t/testing "diff"
      (with-redefs [u.dep/get-scm-url (constantly "https://github.com/foo/bar")
                    u.git/tags-by-ls-remote (constantly ["v1" "v2"])
                    changelog/get-root-file-names (constantly [])]
        (t/is (= (assoc dummy-dep :changes-url "https://github.com/foo/bar/compare/v1...v2")
                 (sut/assoc-changes-url dummy-dep)))

        (t/is (= (assoc dummy-dep :type :test)
                 (sut/assoc-changes-url (assoc dummy-dep :type :test)))))))

  (t/testing "timed out to fetch diffs"
    (let [dummy-dep {:type :java :name "foo/bar" :version "1" :latest-version "2"}]
      (with-redefs [u.dep/get-scm-url (fn [& _] (throw (u.ex/ex-timeout "test")))]
        (t/is (= dummy-dep
                 (sut/assoc-changes-url dummy-dep))))))

  (t/testing "timed out dependencies"
    (let [timed-out-dep {:type :java
                         :name "foo/bar"
                         :version "1"
                         :latest-version (u.ex/ex-timeout "test")}]
      (with-redefs [u.dep/get-scm-url (fn [& _] (throw (Exception. "must not be called")))]
        (t/is (= timed-out-dep
                 (sut/assoc-changes-url timed-out-dep)))))))

(t/deftest unverified-deps-test
  (let [dummy-deps [{:type :java :name "antq/antq"}
                    {:type :java :name "seancorfield/next.jdbc"}
                    {:type :java :name "dummy/dummy"}
                    {:type :UNKNOWN :name "antq/antq"}]]
    (t/is (= [{:type :java
               :name "antq/antq"
               :version "antq/antq"
               :latest-version nil
               :latest-name "com.github.liquidz/antq"}
              {:type :java
               :name "seancorfield/next.jdbc"
               :version "seancorfield/next.jdbc"
               :latest-version nil
               :latest-name "com.github.seancorfield/next.jdbc"}]
             (sut/unverified-deps dummy-deps)))))

(t/deftest fetch-deps-test
  (t/is (seq (sut/fetch-deps {:directory ["."]})))

  (t/testing "skip"
    (t/testing "boot"
      (t/is (nil? (some #(= "test/resources/dep/build.boot" (:file %))
                        (sut/fetch-deps {:directory ["test/resources/dep"]
                                         :skip ["boot"]})))))

    (t/testing "clojure-cli"
      (t/is (nil? (some #(= "test/resources/dep/deps.edn" (:file %))
                        (sut/fetch-deps {:directory ["test/resources/dep"]
                                         :skip ["clojure-cli"]})))))

    (t/testing "github-action"
      (t/is (nil? (some #(= "test/resources/dep/github_action.yml" (:file %))
                        (sut/fetch-deps {:directory ["test/resources/dep"]
                                         :skip ["github-action"]})))))

    (t/testing "pom"
      (t/is (nil? (some #(= "test/resources/dep/pom.xml" (:file %))
                        (sut/fetch-deps {:directory ["test/resources/dep"]
                                         :skip ["pom"]})))))

    (t/testing "shadow-cljs"
      (t/is (nil? (some #(#{"test/resources/dep/shadow-cljs.edn"
                            "test/resources/dep/shadow-cljs-env.edn"} (:file %))
                        (sut/fetch-deps {:directory ["test/resources/dep"]
                                         :skip ["shadow-cljs"]})))))

    (t/testing "leiningen"
      (t/is (nil? (some #(= "test/resources/dep/project.clj" (:file %))
                        (sut/fetch-deps {:directory ["test/resources/dep"]
                                         :skip ["leiningen"]})))))))

(t/deftest mark-only-newest-version-flag-test
  (let [deps [(r/map->Dependency {:name "org.clojure/clojure" :version "1"})
              (r/map->Dependency {:name "org.clojure/clojure" :version "2"})
              (r/map->Dependency {:name "dummy" :version "3"})]
        res (sut/mark-only-newest-version-flag deps)]
    (t/is (= 3 (count res)))
    (t/is (= #{(r/map->Dependency {:name "org.clojure/clojure" :version "1" :only-newest-version? true})
               (r/map->Dependency {:name "org.clojure/clojure" :version "2" :only-newest-version? true})
               (r/map->Dependency {:name "dummy" :version "3" :only-newest-version? nil})}
             (set res)))))

(t/deftest unify-deps-having-only-newest-version-flag-test
  (let [deps [(r/map->Dependency {:name "foo" :version "1.8.0" :file "deps.edn" :only-newest-version? true})
              (r/map->Dependency {:name "foo" :version "1.9.0" :file "deps.edn" :only-newest-version? true})
              (r/map->Dependency {:name "foo" :version "1.10.2" :file "deps.edn" :only-newest-version? true})

              (r/map->Dependency {:name "foo" :version "1.8.0" :file "project.clj" :only-newest-version? true})
              (r/map->Dependency {:name "foo" :version "1.9.0" :file "project.clj" :only-newest-version? true})

              (r/map->Dependency {:name "bar" :version "1.8.0" :file "project.clj" :only-newest-version? false})
              (r/map->Dependency {:name "bar" :version "1.9.0" :file "project.clj" :only-newest-version? false})]
        res (sut/unify-deps-having-only-newest-version-flag deps)]
    (t/is (= 4 (count res)))
    (t/is (= #{(r/map->Dependency {:name "foo" :version "1.10.2" :file "deps.edn" :only-newest-version? true})
               (r/map->Dependency {:name "foo" :version "1.9.0" :file "project.clj" :only-newest-version? true})
               (r/map->Dependency {:name "bar" :version "1.8.0" :file "project.clj" :only-newest-version? false})
               (r/map->Dependency {:name "bar" :version "1.9.0" :file "project.clj" :only-newest-version? false})}
             (set res)))))

(t/deftest latest-test
  (t/is (= "3.0.0"
           (str/trim
            (with-out-str
              (sut/latest {:type :test :name 'foo/bar}))))))

(t/deftest forced-artifacts-test
  (t/testing "default"
    (t/is [] (sut/forced-artifact-version-map {:focus ["foo"]}))
    (t/is [{:name "foo" :latest-version "2.0.0"}] (sut/forced-artifact-version-map {:focus ["foo@2.0.0"]}))
    (t/is [{:name "foo" :latest-version "2.0.0"}
           {:name "foo/zbar2" :latest-version "2"}] (sut/forced-artifact-version-map {:focus ["foo@2.0.0" "foo" "foo/bar" "foo/zbar2@2"]}))))
