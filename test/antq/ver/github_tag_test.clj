(ns antq.ver.github-tag-test
  (:require
   [antq.record :as r]
   [antq.test-report]
   [antq.util.exception :as u.ex]
   [antq.util.git :as u.git]
   [antq.ver :as ver]
   [antq.ver.github-tag :as sut]
   [clojure.data.json :as json]
   [clojure.java.shell :as sh]
   [clojure.string :as str]
   [clojure.test :as t]))

(defn- dep
  [m]
  (r/map->Dependency (merge {:type :github-tag} m)))

(t/deftest tag-api-url-test
  (t/are [expected in] (= expected (sut/tag-api-url {:name in}))
    "https://api.github.com/repos/foo/bar/tags" "foo/bar"
    "https://api.github.com/repos/foo/bar/tags" "foo/bar/baz"))

(defn- get-sorted-versions
  [m]
  (ver/get-sorted-versions (dep (merge {:version "1.0.0"} m))
                           {}))

(def ^:private dummy-json
  (json/write-str
   [{:name "1.0.0"}
    {:name "v2.0.0"}
    {:name "v3.0.0"}
    {:name "v2.0.0-alpha1"}
    {:name "v2.0.0-alpha2"}]))

(defn- reset-fixture
  [f]
  (reset! (deref #'sut/failed-to-fetch-from-api) false)
  (f))

(t/use-fixtures :each reset-fixture)

(t/deftest get-sorted-versions-test
  (reset! @#'sut/failed-to-fetch-from-api false)
  (with-redefs [slurp (constantly dummy-json)]
    (t/is (= ["v3.0.0" "v2.0.0" "v2.0.0-alpha2" "v2.0.0-alpha1" "1.0.0"]
             (get-sorted-versions {:name "foo/bar"}))))

  (t/testing "response should be cached"
    (t/is (= ["v3.0.0" "v2.0.0" "v2.0.0-alpha2" "v2.0.0-alpha1" "1.0.0"]
             (get-sorted-versions {:name "foo/bar"})))))

(t/deftest get-sorted-versions-fallback-test
  (reset! @#'sut/failed-to-fetch-from-api false)
  (let [api-errored (atom false)
        dummy-out (->> [["foo-sha" "FOO"]
                        ["one-sha" "refs/tags/1.0"]
                        ["two-sha" "refs/tags/v2.0"]
                        ["two-sha" "refs/tags/v3.0"]
                        ["two-sha" "refs/tags/v2.0-beta1"]
                        ["bar-sha" "BAR"]]
                       (map #(str/join "\t" %))
                       (str/join "\n"))]
    (with-redefs [;; Disable memoize
                  u.git/ls-remote #'u.git/ls-remote*-with-timeout
                  u.git/tags-by-ls-remote #'u.git/tags-by-ls-remote*
                  sut/get-sorted-versions-by-ls-remote #'sut/get-sorted-versions-by-ls-remote*
                  sut/get-sorted-versions-by-url #'sut/get-sorted-versions-by-url*

                  slurp (fn [& _]
                          (reset! api-errored true)
                          (throw (Exception. "test exception")))
                  sh/sh (fn [& args]
                          (when (= ["git" "ls-remote" "https://github.com/bar/baz"] args)
                            {:out dummy-out
                             :exit 0}))]
      (t/testing "pre"
        (t/is (false? @api-errored))
        (t/is (false? @(deref #'sut/failed-to-fetch-from-api))))

      (t/is (= ["v3.0" "v2.0" "v2.0-beta1" "1.0"]
               (get-sorted-versions {:name "bar/baz"})))

      (t/testing "post"
        (t/is (true? @api-errored))
        (t/is (true? @(deref #'sut/failed-to-fetch-from-api)))))))

(t/deftest get-sorted-versions-timeout-test
  (reset! @#'sut/failed-to-fetch-from-api true)
  (with-redefs [sut/get-sorted-versions-by-ls-remote #'sut/get-sorted-versions-by-ls-remote*
                u.git/tags-by-ls-remote (fn [& _] (throw (u.ex/ex-timeout "test timeout")))]

    (let [deps (get-sorted-versions {:name "foo/bar"})]
      (t/is (= 1 (count deps)))
      (t/is (u.ex/ex-timeout? (first deps))))))

(defn- latest?
  [m]
  (ver/latest? (dep m)))

(t/deftest latest?-test
  (t/are [expected current latest] (= expected (latest? {:version current :latest-version latest}))
    true "3.3.4" "2.3.4"
    true "3.3" "2.3.4"
    true "3" "2.3.4"

    true "2.3.4" "2.3.4"
    true "2.3" "2.3.4"
    true "2" "2.3.4"

    false "1.3.4" "2.3.4"
    false "1.3" "2.3.4"
    false "1" "2.3.4"

    false "2.2.4" "2.3.4"
    false "2.2" "2.3.4"

    false "2.3.3" "2.3.4"

    false "v1" "v2"

    ;; qualified version
    false "1.0.0-alpha1" "1.0.0-alpha2"
    true "1.0.0-alpha2" "1.0.0-alpha1"

    ;; if version tag is unparseable, just log an error and return true.
    true "v2.1.0" "v.2.x"
    true "v.2.x" "v2.1.0"))

(t/deftest latest?-timeout-test
  (t/is (false? (latest? {:version "1" :latest-version (u.ex/ex-timeout "dummy")}))))
