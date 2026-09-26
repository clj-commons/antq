(ns antq.upgrade.pom-test
  (:require
   [antq.dep.pom :as dep.pom]
   [antq.record :as r]
   [antq.test-helper :as h]
   [antq.upgrade :as upgrade]
   [antq.upgrade.pom]
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :as t])
  (:import
   java.io.File))

(def ^:private dummy-java-dep
  (r/map->Dependency {:project :pom
                      :type :java
                      :name "foo/core"
                      :latest-version "9.0.0"
                      :file (io/resource "dep/test_pom.xml")}))

(def ^:private dummy-prop-java-dep
  (r/map->Dependency {:project :pom
                      :type :java
                      :name "org.clojure/clojure"
                      :latest-version "9.0.0"
                      :file (io/file (io/resource "dep/test_pom_properties.xml"))}))

(def ^:private dummy-parent-child-java-dep
  (r/map->Dependency {:project :pom
                      :type :java
                      :name "org.clojure/clojure"
                      :latest-version "9.0.0"
                      :file (io/file (io/resource "dep/child_pom/child/pom.xml"))}))

(def ^:private dummy-java-with-exclusion-dep
  (r/map->Dependency {:project :pom
                      :type :java
                      :name "com.bar/bar"
                      :latest-version "9.0.0"
                      :file (io/file (io/resource "dep/test_pom_exclusions.xml"))}))

(t/deftest upgrade-dep-test
  (let [tmp-file (File/createTempFile "upgrade-dep-test" "xml")]
    (try
      (let [from-deps (->> dummy-java-dep
                           :file
                           (dep.pom/extract-deps ""))

            _ (->> dummy-java-dep
                   (upgrade/upgrader)
                   (spit tmp-file))
            to-deps (dep.pom/extract-deps "" tmp-file)]
        (t/is (= #{{:name "foo/core" :version {:- "1.0.0" :+ "9.0.0"}}
                   {:name "foo/core" :version {:- "1.1.0" :+ "9.0.0"}}}
                 (h/diff-deps from-deps to-deps))))
      (finally
        (.delete tmp-file)))))

(t/deftest upgrade-dep-with-properties-test
  (let [tmp-file (File/createTempFile "upgrade-dep-test" "xml")]
    (try
      (let [from-deps (->> dummy-prop-java-dep
                           :file
                           (dep.pom/extract-deps ""))
            _ (->> dummy-prop-java-dep
                   (upgrade/upgrader)
                   (spit tmp-file))
            to-deps (dep.pom/extract-deps "" tmp-file)]
        (t/is (= #{{:name "org.clojure/clojure" :version {:- "1.4.0" :+ "9.0.0"}}}
                 (h/diff-deps from-deps to-deps)))

        (t/testing "properties should be updated"
          (t/is (= #{{:- "    <clojure.version>1.4.0</clojure.version>"
                      :+ "    <clojure.version>9.0.0</clojure.version>"}}
                   (h/diff-lines
                    (str/split-lines (slurp (:file dummy-prop-java-dep)))
                    (str/split-lines (slurp tmp-file)))))))
      (finally
        (.delete tmp-file)))))

(t/deftest upgrade-dep-with-child-pom-test
  (t/is (nil? (->> dummy-parent-child-java-dep
                   (upgrade/upgrader)))))

(t/deftest upgrade-dep-with-exclusion-test
  (let [tmp-file (File/createTempFile "upgrade-dep-test" "xml")]
    (try
      (let [from-deps (->> dummy-java-with-exclusion-dep
                           :file
                           (dep.pom/extract-deps ""))
            _ (->> dummy-java-with-exclusion-dep
                   (upgrade/upgrader)
                   (spit tmp-file))
            to-deps (dep.pom/extract-deps "" tmp-file)]
        (t/is (= #{{:name "com.bar/bar" :version {:- "1.0.0" :+ "9.0.0"}}}
                 (h/diff-deps from-deps to-deps))))
      (finally
        (.delete tmp-file)))))

(t/deftest pom-upgrade-preserves-line-endings-test
  (doseq [[expected-eol input]
          [["\n"   "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<foo><bar><baz>The baz value</baz></bar></foo>\n"]
           ["\r\n" "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n<foo><bar><baz>The baz value</baz></bar></foo>\r\n"]]]
    (fs/with-temp-dir [d]
      (let [f (fs/file d "pom.xml")]
        (spit f input)
        ;; XML is pretty printed, so won't exactly equal input
        (let [eols (->> (upgrade/upgrader {:file f
                                           :project :pom
                                           :name "foo/bar"})
                        (re-seq #"(\r\n|\n)")
                        (mapv last))]
          (t/is (pos? (count eols)))
          (t/is (every? #(= expected-eol %) eols)))))))
