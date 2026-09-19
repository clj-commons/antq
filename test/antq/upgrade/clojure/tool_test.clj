(ns antq.upgrade.clojure.tool-test
  (:require
   [antq.dep.clojure.tool :as dep.clj.tool]
   [antq.record :as r]
   [antq.test-helper :as h]
   [antq.upgrade :as upgrade]
   [antq.upgrade.clojure.tool]
   [antq.util.git :as u.git]
   [clojure.java.io :as io]
   [clojure.test :as t]))

(def ^:private dummy-dep
  (r/map->Dependency {:project :clojure-tool
                      :type :git-tag-and-sha
                      :name "com.github.liquidz/antq"
                      :latest-version "9.9.9"
                      :file (io/resource "dep/clojure-cli-tool/dummy.edn")
                      :extra {:url "https://github.com/clj-commons/antq.git"
                              :sha "bc28de6"}}))

(t/deftest upgrade-dep-test
  (with-redefs [u.git/tag-sha-by-ls-remote (constantly "9876543210abcdefghijklmnopqrstuvwxyz1234")]
    (let [from-deps (->> dummy-dep
                         :file
                         (slurp)
                         (dep.clj.tool/extract-deps "")
                         (vector))
          to-deps (->> dummy-dep
                       (upgrade/upgrader)
                       (dep.clj.tool/extract-deps "")
                       (vector))]
      (t/is (= #{{:name "com.github.liquidz/antq"
                  :version {:- "1.5.1" :+ "9.9.9"}
                  :url "https://github.com/clj-commons/antq.git"
                  :sha {:- "bc28de64fb36b395da4267e8d7916d1a9e167bcd"
                        :+ "9876543"}}}
               (h/diff-deps from-deps to-deps))))))
