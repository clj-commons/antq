(ns antq.dep.transitive-test
  (:require
   [antq.dep.transitive :as sut]
   [antq.record :as r]
   [clojure.test :as t]
   [clojure.tools.deps :as deps]))

(def ^:private dummy-dep
  {:type :java
   :name "foo/alice"
   :version "1.0.0"})

(defn- mock-resolve-deps
  [deps-map _]
  (condp = (ffirst (:deps deps-map))
    'foo/alice {'foo/bob {:deps/manifest :mvn
                          :mvn/version "2.0.0"
                          :dependents ['foo/alice]}}
    'foo/bob {'foo/charlie {:deps/manifest :deps
                            :git/sha "1234567890"
                            :git/url "https://example.com/charlie"
                            :dependents ['foo/bob]}
              'foo/dave {:deps/manifest :deps
                         :git/tag "v3"
                         :git/sha "0987654321"
                         :git/url "https://example.com/dave"
                         :dependents ['foo/bob]}}
    {}))

(def ^:private custom-repo-dep
  {:type :java
   :name "external/zulu"
   :version "0.1.0"
   :repositories {"external-repo" "https://repo.example.com"}})

(defn- mock-resolve-deps-custom-repo
  [{:keys [deps mvn/repos]} _]
  (when-not (contains? repos "external-repo")
    (throw (ex-info "missing expected repository" {:mvn/repos repos})))
  (condp = (ffirst deps)
    'external/zulu {'external/yankee {:deps/manifest :mvn
                                      :mvn/version "0.2.0"
                                      :dependents ['external/zulu]}}
    'external/yankee {'external/x-ray {:deps/manifest :mvn
                                       :mvn/version "0.3.0"
                                       :dependents ['external/yankee]}}
    {}))

(t/deftest dep->dep-map-test
  (t/testing "java"
    (t/is (= {'foo/bar {:mvn/version "1.2.3"}}
             (#'sut/dep->dep-map {:type :java
                                  :name "foo/bar"
                                  :version "1.2.3"}))))
  (t/testing "git-sha with git/url"
    (t/is (= {'foo/bar {:git/sha "86eddb89f2a2018fd984dffaadfec13e6735e92f"
                        :git/url "https://github.com/clj-commons/antq"}}
             (#'sut/dep->dep-map {:type :git-sha
                                  :name "foo/bar"
                                  :version "86eddb89f2a2018fd984dffaadfec13e6735e92f"
                                  :extra {:url "https://github.com/clj-commons/antq"}}))))
  (t/testing "git-sha without git/url"
    (t/is (= {'com.github.liquidz/antq {:git/sha "86eddb89f2a2018fd984dffaadfec13e6735e92f"}}
             (#'sut/dep->dep-map {:type :git-sha
                                  :name "com.github.liquidz/antq"
                                  :version "86eddb89f2a2018fd984dffaadfec13e6735e92f"}))))
  (t/testing "git-tag-and-sha with git/url"
    (t/is (= {'foo/bar {:git/tag "1.2.3"
                        :git/sha "86eddb8"
                        :git/url "https://github.com/clj-commons/antq"}}
             (#'sut/dep->dep-map {:type :git-tag-and-sha
                                  :name "foo/bar"
                                  :version "1.2.3"
                                  :extra {:sha "86eddb8"
                                          :url "https://github.com/clj-commons/antq"}}))))
  (t/testing "git-tag-and-sha without git/url"
    (t/is (= {'com.github.liquidz/antq {:git/tag "1.2.3"
                                        :git/sha "86eddb8"}}
             (#'sut/dep->dep-map {:type :git-tag-and-sha
                                  :name "com.github.liquidz/antq"
                                  :version "1.2.3"
                                  :extra {:sha "86eddb8"}})))))

(t/deftest resolve-transitive-deps-test
  (with-redefs [deps/resolve-deps mock-resolve-deps]
    (t/is (= [(r/map->Dependency {:type :java
                                  :name "foo/bob"
                                  :version "2.0.0"
                                  :file ""
                                  :parent "foo/alice"})
              (r/map->Dependency {:type :git-sha
                                  :name "foo/charlie"
                                  :version "1234567890"
                                  :file ""
                                  :parent "foo/bob"
                                  :extra {:url "https://example.com/charlie"}})
              (r/map->Dependency {:type :git-tag-and-sha
                                  :name "foo/dave"
                                  :version "v3"
                                  :file ""
                                  :parent "foo/bob"
                                  :extra {:sha "0987654321"
                                          :url "https://example.com/dave"}})]
             (->> (sut/resolve-transitive-deps [dummy-dep])
                  (sort-by :name))))))

(t/deftest resolve-transitive-deps-custom-repo-test
  (with-redefs [deps/resolve-deps mock-resolve-deps-custom-repo]
    (t/is (= [(r/map->Dependency {:type :java
                                  :name "external/x-ray"
                                  :version "0.3.0"
                                  :repositories {"external-repo" "https://repo.example.com"}
                                  :file ""
                                  :parent "external/yankee"})
              (r/map->Dependency {:type :java
                                  :name "external/yankee"
                                  :version "0.2.0"
                                  :repositories {"external-repo" "https://repo.example.com"}
                                  :file ""
                                  :parent "external/zulu"})]
             (->> (sut/resolve-transitive-deps [custom-repo-dep])
                  (sort-by :name))))))
