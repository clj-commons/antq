(ns build
  (:require
   [build-edn.core :as build-edn]))

(def ^:private config
  {:lib 'com.github.liquidz/antq
   :version "2.11.{{git/commit-count}}"
   :description "Point out your outdated dependencies"
   :licenses [{:name "Eclipse Public License - v 2.0"
               :url "https://www.eclipse.org/legal/epl-2.0/"}]
   :main 'antq.core
   :documents [{:file "CHANGELOG.adoc"
                :match "Unreleased"
                :action :append-after
                :text "\n== {{version}} ({{now/yyyy}}-{{now/mm}}-{{now/dd}})"}]
   :github-actions? true})

(defn jar
  [m]
  (-> (merge config m)
      (build-edn/jar)))

(defn uberjar
  [m]
  (-> (merge config m)
      (assoc
       ;; NOTE: To include org.slf4j/slf4j-nop
       :aliases [:nop]
       ;; NOTE: does not contain src/leiningen
       :source-dirs ["src/antq"])
      (build-edn/uberjar)))

(defn install
  [m]
  (-> (merge config m)
      (build-edn/install)))

(defn deploy
  [m]
  (let [config (merge config m)]
    (build-edn/deploy (assoc config :lib 'antq/antq))
    (build-edn/deploy config)))

(defn update-documents
  [m]
  (-> (merge config m)
      (build-edn/update-documents)))

(defn lint
  [m]
  (-> (merge config m)
      (build-edn/lint)))
