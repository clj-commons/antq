(ns antq.packaging-test
  "Test whether we are packaging our resources properly."
  (:require
   [babashka.fs :as fs]
   [babashka.process :as p]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]))

(defn- tools-deps-version
  []
  (-> "deps.edn"
      slurp
      edn/read-string
      :deps
      (get 'org.clojure/tools.deps)
      (get :mvn/version)))

(deftest ^:integration jar-includes-resources
  (fs/delete-if-exists "target/antq.jar")
  (p/shell "bb jar")
  (let [version (-> (io/resource "com.github.liquidz/antq/VERSION") slurp str/trim)
        {:keys [out]} (p/shell {:out :string}
                               "bb -Sdeps"
                               (pr-str {:deps {'com.github.liquidz/antq {:local/root "target/antq.jar"}}})
                               "--eval" "(require 'antq.impl.antq-meta) (println antq.impl.antq-meta/user-agent)")
        out (str/trim out)]
    (is (= (format "antq/%s tools.deps/%s" version (tools-deps-version)) out) "user-agent")))

(deftest ^:integration uberjar-includes-resources
  (fs/delete-if-exists "target/antq-standalone.jar")
  (p/shell "bb uberjar")
  (let [version (-> (io/resource "com.github.liquidz/antq/VERSION") slurp str/trim)
        {:keys [out]} (p/shell {:out :string}
                               "clojure -Scp target/antq-standalone.jar -M --eval"
                               "(require 'antq.impl.antq-meta) (println antq.impl.antq-meta/user-agent)")
        out (str/trim out)]
    (is (= (format "antq/%s tools.deps/%s" version (tools-deps-version)) out) "user-agent")))
