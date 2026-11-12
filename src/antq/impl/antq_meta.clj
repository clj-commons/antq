(ns ^:no-doc antq.impl.antq-meta
  "Metadata about antq itself"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def antq-version
  (-> (io/resource "com.github.liquidz/antq/VERSION")
      slurp
      str/trim))

(def ^:private tools-deps-version
  (let [version-line (->> (io/resource "META-INF/maven/org.clojure/tools.deps/pom.properties")
                          slurp
                          (str/split-lines)
                          (filter #(str/starts-with? % "version="))
                          first)]
    (-> version-line
        (str/replace "version=" "")
        str/trim)))

(def user-agent
  "As per convention discussed for clojars"
  (str "antq/" antq-version " tools.deps/" tools-deps-version))

(comment
  antq-version
  ;; => "2.12.119-SNAPSHOT"

  tools-deps-version
  ;; => "0.31.1646"

  user-agent
  ;; => "antq/2.12.119-SNAPSHOT tools.deps/0.31.1646"

  :eoc)
