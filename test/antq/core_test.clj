(ns antq.core-test
  (:require
   [antq.changelog :as changelog]
   [antq.core :as sut]
   [antq.log :as log]
   [antq.record :as r]
   [antq.test-helper :as h]
   [antq.test-report]
   [antq.upgrade :as upgrade]
   [antq.util.dep :as u.dep]
   [antq.util.exception :as u.ex]
   [antq.util.git :as u.git]
   [antq.util.maven :as u.maven]
   [antq.util.os :as os]
   [antq.ver :as ver]
   [babashka.fs :as fs]
   [clojure.string :as str]
   [clojure.test :as t]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test])
  (:import
   (java.time
    Duration
    Instant)))

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
  (t/testing "`focus` should be preferred over `exclude`"
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

(defn- days-ago
  [n]
  (.minus (Instant/now) (Duration/ofMinutes (long (* n 24 60)))))

(t/deftest outdated-deps-cooldown-test
  (let [dates {"4.0.0" (days-ago 1) "3.0.0" (days-ago 5) "2.0.0" (days-ago 30) "1.0.0" (days-ago 100)}
        looked-up (atom [])
        dep (r/map->Dependency {:type :java :name "foo/bar" :version "1.0.0"})
        outdated (fn [options & {:keys [deps dates versions]
                                 :or {deps [dep] dates dates versions ["4.0.0" "3.0.0" "2.0.0" "1.0.0"]}}]
                   (reset! looked-up [])
                   (with-redefs [ver/get-sorted-versions (fn [d _] (if (map? versions) (versions (:name d)) versions))
                                 u.maven/release-date (fn [_ _ v] (swap! looked-up conj v) (get dates v))]
                     (sut/outdated-deps deps options)))]

    (t/testing "release dates are added without cooldown"
      (t/is (match? [{:latest-version "4.0.0"
                      :released-at (str (dates "1.0.0"))
                      :latest-released-at (str (dates "4.0.0"))
                      :cooldown-days m/absent
                      :cooldown-version m/absent
                      :cooldown-released-at m/absent}]
                    (outdated {})))
      (t/is (match? [{:cooldown-days m/absent}] (outdated {:cooldown 0}))))

    (t/testing "the newest version outside the cooldown is picked"
      (t/is (match? [{:latest-version "4.0.0" :cooldown-days 7 :cooldown-version "2.0.0"
                      :cooldown-released-at (str (dates "2.0.0"))}]
                    (outdated {:cooldown 7})))
      (t/is (match? [{:cooldown-version "3.0.0"}] (outdated {:cooldown 3})))
      (t/testing "and older versions are not looked up"
        (t/is (not-any? #{"2.0.0"} @looked-up))))

    (t/testing "all newer versions are inside the cooldown; the current version is not picked"
      (t/is (match? [{:latest-version "4.0.0" :cooldown-version nil :cooldown-released-at nil}]
                    (outdated {:cooldown 50}))))

    (t/testing "a version released exactly cooldown days ago passes"
      (t/is (match? [{:cooldown-version "4.0.0"}]
                    (outdated {:cooldown 7} :dates (assoc dates "4.0.0" (days-ago 7)))))
      (t/is (match? [{:cooldown-version "3.0.0"}]
                    (outdated {:cooldown 7} :dates (assoc dates
                                                          "4.0.0" (.plusSeconds ^Instant (days-ago 7) 60)
                                                          "3.0.0" (days-ago 8))))))

    (t/testing "a version without release date counts as too new"
      (t/is (match? [{:cooldown-version "2.0.0" :latest-released-at nil}]
                    (outdated {:cooldown 3} :dates (dissoc dates "4.0.0" "3.0.0")))))

    (t/testing "excluded versions are not picked"
      (t/is (match? [{:cooldown-version nil}] (outdated {:cooldown 7 :exclude ["foo/bar@2.0.0"]}))))

    (t/testing "pre-releases are picked only for a pre-release, like the latest version"
      (let [versions ["3.0.0-alpha1" "2.0.0" "2.0.0-rc1" "1.1.0" "1.0.0"]
            dates {"3.0.0-alpha1" (days-ago 1) "2.0.0" (days-ago 2) "2.0.0-rc1" (days-ago 20) "1.1.0" (days-ago 40)}]
        (t/is (match? [{:latest-version "2.0.0" :cooldown-version "1.1.0"}]
                      (outdated {:cooldown 7} :versions versions :dates dates)))
        (t/is (match? [{:latest-version "3.0.0-alpha1" :cooldown-version "2.0.0-rc1"}]
                      (outdated {:cooldown 7} :versions versions :dates dates
                                :deps [(assoc dep :version "2.0.0-beta1")])))))

    (t/testing "a timed-out version lookup gets no dates"
      (t/is (match? [{:latest-version u.ex/ex-timeout? :released-at m/absent :cooldown-days m/absent}]
                    (outdated {:cooldown 7} :versions [(u.ex/ex-timeout "test")])))
      (t/is (empty? @looked-up)))

    (t/testing "dates come from the dep's own repositories"
      (let [repos (atom nil)]
        (with-redefs [ver/get-sorted-versions (constantly ["2.0.0" "1.0.0"])
                      u.maven/release-date (fn [r _ _] (reset! repos r) nil)]
          (sut/outdated-deps [(assoc dep :repositories {"private" {:url "https://repo.example.com"}})] {}))
        (t/is (match? {"private" {:url "https://repo.example.com"} "central" map?} @repos))))

    (t/testing "a version forced with --focus ignores the cooldown"
      (t/is (match? [{:latest-version "4.0.0" :cooldown-days m/absent}]
                    (outdated {:cooldown 7 :focus ["foo/bar@4.0.0"]}))))

    (t/testing "up-to-date deps are not looked up"
      (t/is (empty? (outdated {:cooldown 7} :deps [(assoc dep :version "4.0.0")])))
      (t/is (empty? @looked-up)))

    (t/testing "non-Maven deps get no dates and no cooldown"
      (t/is (match? [{:released-at m/absent :cooldown-days m/absent}]
                    (outdated {:cooldown 7} :deps [(assoc dep :type :test)])))
      (t/is (empty? @looked-up)))

    (t/testing "--transitive: a held dep listed with its parent gives exit code 0"
      (let [deps [(assoc dep :name "foo/parent")
                  (assoc dep :name "foo/child" :parent "foo/parent")]
            versions {"foo/parent" ["1.0.0"] "foo/child" ["4.0.0" "1.0.0"]}
            exit-code #(with-redefs [sut/system-exit identity] (sut/exit %))]
        (t/is (match? [{:name "foo/parent"} {:name "foo/child" :cooldown-version nil}]
                      (outdated {:cooldown 7} :deps deps :versions versions)))
        (t/is (= 0 (exit-code (outdated {:cooldown 7} :deps deps :versions versions))))
        (t/is (= 1 (exit-code (outdated {} :deps deps :versions versions))))))))

(t/deftest exit-test
  (let [held {:latest-version "2" :cooldown-days 7 :cooldown-version nil}
        picked {:latest-version "2" :cooldown-days 7 :cooldown-version "2"}
        exit-code #(with-redefs [sut/system-exit identity] (sut/exit %))]
    (t/is (= 0 (exit-code [])))
    (t/is (= 0 (exit-code [held held])))
    (t/is (= 1 (exit-code [held picked])))
    (t/is (= 1 (exit-code [{:latest-version "2"}])))
    (t/testing "a version forced with --focus counts even when it is not newer"
      (t/is (= 1 (exit-code [{:type :java :version "2" :latest-version "1" :forced-version "1"}]))))
    (t/testing "failed version lookup"
      (t/is (= 1 (exit-code [{:latest-version nil}]))))))

(defmethod upgrade/upgrader ::cooldown
  [dep]
  (:latest-version dep))

(t/deftest main*-cooldown-test
  (let [file (str (fs/create-temp-file))
        dep (r/map->Dependency {:type :java :project ::cooldown :file file :version "1.0.0"})
        deps [(assoc dep :name "main/picked") (assoc dep :name "main/held")]
        versions {"main/picked" ["3.0.0" "2.0.0" "1.0.0"] "main/held" ["3.0.0" "1.0.0"]}
        dates {"3.0.0" (days-ago 1) "2.0.0" (days-ago 30)}
        run (fn [opts]
              (spit file "")
              (let [exit-code (atom nil)]
                (with-redefs [u.maven/initialize-proxy-setting! (constantly nil)
                              sut/fetch-deps (constantly deps)
                              ver/get-sorted-versions (fn [d _] (versions (:name d)))
                              u.maven/release-date (fn [_ _ v] (get dates v))
                              sut/system-exit identity]
                  (with-out-str
                    (reset! exit-code (sut/main* {:opts (assoc opts :no-changes true :reporter "edn")}))))
                [@exit-code (slurp file)]))]
    (try
      (t/testing "report only: the picked dep is outdated"
        (t/is (= [1 ""] (run {:cooldown 7}))))
      (t/testing "upgrade writes the cooldown version; a held dep gives exit code 0"
        (t/is (= [0 "2.0.0"] (run {:cooldown 7 :upgrade true :force true}))))
      (t/testing "without cooldown both deps go to the latest version"
        (t/is (= [0 "3.0.0"] (run {:upgrade true :force true}))))
      (finally
        (fs/delete-if-exists file)))))

(defmethod ver/get-sorted-versions :test-pre-release
  [dep _]
  (get {"flow" ["4.0.0-pre5" "4.0.0-pre1" "3.0.0" "2.0.0"]
        "spock" ["2.4-M7-groovy-5.0" "2.4-M7-groovy-4.0" "2.3-groovy-4.0" "2.2-groovy-4.0"]}
       (:name dep)))

(t/deftest outdated-deps-pre-release-test
  (let [dep #(r/map->Dependency {:type :test-pre-release :name %1 :version %2})
        outdated #(sut/outdated-deps [(dep %1 %2)] {})]
    (t/testing "a pre-release is not suggested for a release"
      (t/is (= [] (outdated "flow" "3.0.0")))
      (t/is (= [(assoc (dep "flow" "2.0.0") :latest-version "3.0.0")]
               (outdated "flow" "2.0.0")))
      (t/is (= [] (outdated "spock" "2.3-groovy-4.0")))
      (t/is (= [(assoc (dep "spock" "2.2-groovy-4.0") :latest-version "2.3-groovy-4.0")]
               (outdated "spock" "2.2-groovy-4.0"))))

    (t/testing "a newer pre-release is suggested for a pre-release"
      (t/is (= [(assoc (dep "flow" "4.0.0-pre1") :latest-version "4.0.0-pre5")]
               (outdated "flow" "4.0.0-pre1"))))))

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

(defn- dep-files
  [project-dir deps]
  (->> deps
       (mapv :file)
       distinct
       (mapv #(fs/relativize project-dir %))
       (mapv str)
       (mapv h/os-path)
       set))

(defn- setup-deps-scenario
  [target-project-dir]
  (h/setup-deps-scenario
   target-project-dir
   [["build.gradle"]
    ["test_bb.edn"            "bb.edn"]
    ["test_build.boot"        "build.boot"]
    ["test_circle_ci.yml"     ".circleci/config.yml"]
    ["test_deps.edn"          "deps.edn"]
    ["test_github_action.yml" ".github/workflows/test.yml"]
    ["test_pom.xml"           "pom.xml"]
    ["test_project.clj"       "project.clj"]
    ["test_shadow-cljs.edn"   "shadow-cljs.edn"]]))

(t/deftest fetch-deps-test
  (let [project-dir "target/test/fetch-deps-test"
        all-files (setup-deps-scenario project-dir)
        dep-files #(dep-files project-dir %)]

    (t/testing "antq project deps"
      (t/is (match? (m/embeds ["bb.edn"
                               "deps.edn"
                               (if (os/windows?)
                                 #".github\\workflows\\.*"
                                 #".github/workflows/.*")])
                    (->> (sut/fetch-deps {:directory ["."]})
                         (mapv :file)
                         distinct))))

    (t/testing "default"
      (t/is (match? (disj all-files "build.gradle")
                    (dep-files (sut/fetch-deps {:directory [project-dir]})))))

    (t/testing "skip"
      (t/testing "boot"
        (t/is (match? (disj all-files "build.gradle" "build.boot")
                      (dep-files (sut/fetch-deps {:directory [project-dir]
                                                  :skip ["boot"]})))))

      (t/testing "clojure-cli"
        (t/is (match? (disj all-files "build.gradle" "deps.edn")
                      (dep-files (sut/fetch-deps {:directory [project-dir]
                                                  :skip ["clojure-cli"]})))))

      (t/testing "github-action"
        (t/is (match? (disj all-files "build.gradle"
                            (h/os-path ".github/workflows/test.yml"))
                      (dep-files (sut/fetch-deps {:directory [project-dir]
                                                  :skip ["github-action"]})))))

      (t/testing "pom"
        (t/is (match? (disj all-files "build.gradle" "pom.xml")
                      (dep-files (sut/fetch-deps {:directory [project-dir]
                                                  :skip ["pom"]})))))

      (t/testing "shadow-cljs"
        (t/is (match? (disj all-files "build.gradle" "shadow-cljs.edn")
                      (dep-files (sut/fetch-deps {:directory [project-dir]
                                                  :skip ["shadow-cljs"]})))))

      (t/testing "leiningen"
        (t/is (match? (disj all-files "build.gradle" "project.clj")
                      (dep-files (sut/fetch-deps {:directory [project-dir]
                                                  :skip ["leiningen"]})))))
      (t/testing "babashka"
        (t/is (match? (disj all-files "build.gradle" "bb.edn")
                      (dep-files (sut/fetch-deps {:directory [project-dir]
                                                  :skip ["babashka"]}))))))))

(t/deftest ^:gradle fetch-deps-gradle-test
  ;; gradle does not run on the entire broader set of of supported Antq JDKs, hence this tagged
  ;; isolated test to allow for explicit inclusion and exclusion
  (let [project-dir "target/test/fetch-deps-gradle-test"
        all-files (setup-deps-scenario project-dir)
        dep-files #(dep-files project-dir %)]
    (t/testing "hint is logged when not checking gradle"
      (let [log-error (atom [])]
        (with-redefs [log/error (fn [s] (swap! log-error conj s) nil)]
          (t/is (match? (disj all-files "build.gradle")
                        (dep-files (sut/fetch-deps {:directory [project-dir]}))))
          (t/is (match? [(str "Skipping Gradle project discovered via "
                              (h/os-path "target/test/fetch-deps-gradle-test/build.gradle")
                              ", use:\n"
                              " --check-gradle to check it\n"
                              " --skip=gradle to suppress this message")] @log-error)))))
    (t/testing "hint is suppressed with --skip=gradle"
      (let [log-error (atom [])]
        (with-redefs [log/error (fn [s] (swap! log-error conj s) nil)]
          (t/is (match? (disj all-files "build.gradle")
                        (dep-files (sut/fetch-deps {:directory [project-dir]
                                                    :skip ["gradle"]}))))
          (t/is (match? [] @log-error)))))
    (t/testing "--skip=gradle takes precedence over --check-gradle"
      (let [log-error (atom [])]
        (with-redefs [log/error (fn [s] (swap! log-error conj s) nil)]
          (t/is (match? (disj all-files "build.gradle")
                        (dep-files (sut/fetch-deps {:directory [project-dir]
                                                    :skip ["gradle"]
                                                    :check-gradle true}))))
          (t/is (match? [] @log-error)))))
    (t/testing "check all"
      (let [log-error (atom [])]
        (with-redefs [log/error (fn [s] (swap! log-error conj s))]
          (t/is (match? all-files
                        (dep-files (sut/fetch-deps {:directory [project-dir]
                                                    :check-gradle true}))))
          (t/is (match? [] @log-error)))))
    (t/testing "hint is not presented if no gradle project detected"
      ;; NB: altering test scenario
      (fs/delete (fs/path project-dir "build.gradle"))
      (let [log-error (atom [])]
        (with-redefs [log/error (fn [s] (swap! log-error conj s))]
          (t/is (match? (disj all-files "build.gradle")
                        (dep-files (sut/fetch-deps {:directory [project-dir]}))))
          (t/is (match? [] @log-error)))))))

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
    (t/is (= {} (sut/forced-artifact-version-map {:focus ["foo"]})))
    (t/is (= {"foo" "2.0.0"} (sut/forced-artifact-version-map {:focus ["foo@2.0.0"]})))
    (t/is (= {"foo" "2.0.0"
              "foo/zbar2" "2"}
             (sut/forced-artifact-version-map {:focus ["foo@2.0.0" "foo" "foo/bar" "foo/zbar2@2"]})))))
