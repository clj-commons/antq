(ns antq.upgrade.github-action.third-party
  (:require
   [antq.constant.github-action :as const.gh-action]
   [antq.dep.github-action :as dep.gha]
   [antq.record :as r]
   [antq.test-helper :as h]
   [antq.upgrade :as upgrade]
   [antq.upgrade.github-action]
   [clojure.java.io :as io]
   [clojure.test :as t]))

(defn- map->Dependency
  [m]
  (-> (merge {:project :github-action
              :type :github-tag
              :latest-version "9.0.0"
              :file (io/resource "dep/third-party/setup-clojure.yml")
              :extra {const.gh-action/type-key "DeLaGuardo/setup-clojure"}}
             m)
      (r/map->Dependency)))

(defn upgrading-diff
  [dep]
  (let [from-deps (->> (:file dep)
                       (slurp)
                       (dep.gha/extract-deps ""))
        to-deps (->> (upgrade/upgrader dep)
                     (dep.gha/extract-deps ""))]
    (h/diff-deps from-deps to-deps)))

(t/deftest upgrade-setup-clojure-dep-test
  (t/testing "Clojure CLI"
    (t/is (= #{{:name const.gh-action/setup-clojure-name :version {:- 1 :+ "9.0.0"}}
               {:name const.gh-action/setup-clojure-name :version {:- -1 :+ "9.0.0"}}}
             (-> {:name const.gh-action/setup-clojure-name}
                 (map->Dependency)
                 (upgrading-diff)))))

  (t/testing "Leiningen"
    (t/is (= #{{:name const.gh-action/setup-leiningen-name :version {:- 2 :+ "9.0.0"}}
               {:name const.gh-action/setup-leiningen-name :version {:- -2 :+ "9.0.0"}}}
             (-> {:name const.gh-action/setup-leiningen-name}
                 (map->Dependency)
                 (upgrading-diff)))))

  (t/testing "Boot"
    (t/is (= #{{:name const.gh-action/setup-boot-name :version {:- 3 :+ "9.0.0"}}
               {:name const.gh-action/setup-boot-name :version {:- -3 :+ "9.0.0"}}}
             (-> {:name const.gh-action/setup-boot-name}
                 (map->Dependency)
                 (upgrading-diff)))))

  (t/testing "Babashka"
    (t/is (= #{{:name const.gh-action/setup-babashka-name :version {:- 4 :+ "9.0.0"}}
               {:name const.gh-action/setup-babashka-name :version {:- -4 :+ "9.0.0"}}}
             (-> {:name const.gh-action/setup-babashka-name
                  :type :java}
                 (map->Dependency)
                 (upgrading-diff)))))

  (t/testing "clj-kondo"
    (t/is (= #{{:name const.gh-action/setup-clj-kondo-name :version {:- 5 :+ "9.0.0"}}
               {:name const.gh-action/setup-clj-kondo-name :version {:- -5 :+ "9.0.0"}}}
             (-> {:name const.gh-action/setup-clj-kondo-name
                  :type :java}
                 (map->Dependency)
                 (upgrading-diff)))))

  (t/testing "cljfmt"
    (t/is (= #{{:name const.gh-action/setup-cljfmt-name :version {:- 6 :+ "9.0.0"}}
               {:name const.gh-action/setup-cljfmt-name :version {:- -6 :+ "9.0.0"}}}
             (-> {:name const.gh-action/setup-cljfmt-name}
                 (map->Dependency)
                 (upgrading-diff)))))

  (t/testing "cljstyle"
    (t/is (= #{{:name const.gh-action/setup-cljstyle-name :version {:- 7 :+ "9.0.0"}}
               {:name const.gh-action/setup-cljstyle-name :version {:- -7 :+ "9.0.0"}}}
             (-> {:name const.gh-action/setup-cljstyle-name}
                 (map->Dependency)
                 (upgrading-diff)))))

  (t/testing "zprint"
    (t/is (= #{{:name const.gh-action/setup-zprint-name :version {:- 8 :+ "9.0.0"}}
               {:name const.gh-action/setup-zprint-name :version {:- -8 :+ "9.0.0"}}}
             (-> {:name const.gh-action/setup-zprint-name
                  :type :java}
                 (map->Dependency)
                 (upgrading-diff))))))
