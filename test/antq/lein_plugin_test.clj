(ns ^:integration antq.lein-plugin-test
  "These lein plugin integration tests installed antq to your local .m2 maven repo."
  (:require
   [babashka.fs :as fs]
   [babashka.process :as p]
   [clojure.string :as str]
   [clojure.test :refer [deftest is use-fixtures]]
   [flatland.ordered.map :as omap]
   [matcher-combinators.test]))

(def ^:private test-work-dir "target/test/lein-plugin")
(def ^:private antq-test-version "0.0.0-lein-plugin-test")
(def ^:private antq-coords ['com.github.liquidz/antq antq-test-version])

(defn- recreate-work-dir
  []
  (fs/delete-tree test-work-dir)
  (fs/create-dirs test-work-dir))

(use-fixtures :once
  (fn [f]
    (try
      ;; we install to our local .m2 repository, 
      ;; but are careful to not conflict with some real installed antq artifact 
      (p/shell {:out :string} "clojure -T:build install"
               ":version" (pr-str antq-test-version))
      (f)
      ;; take a stab at cleaning up, assume .m2 repo was not overridden in developer's config
      (finally
        (fs/delete-tree (fs/expand-home (str "~/.m2/repository/com/github/liquidz/antq/" antq-test-version)))))))

(use-fixtures :each
  (fn [f]
    (recreate-work-dir)
    (f)))

(defn- lein-project
  [proj-opts]
  (let [default-opts (omap/ordered-map
                      :description "some desc"
                      :antq {:exclude ["nrepl/nrepl" "com.github.liquidz/antq"]})
        opts (merge default-opts (apply omap/ordered-map proj-opts))]
    (spit (fs/file test-work-dir "project.clj")
          (str "(defproject lein-plugin-test \"n/a\"\n"
               (str/join "\n" (into (mapv
                                     (fn [[k v]]
                                       (str "  " k " "
                                            (binding [*print-namespace-maps* false
                                                      *print-meta* true]
                                              (pr-str v))))
                                     opts)))
               ")"))))


(defn- parse-out
  "Returns interesting output as line"
  [out]
  (->> out
       str/split-lines
       (remove (fn [l] (re-find #"^(SLF4J:|Retrieving|Downloading|\[|\| :f|\|-| *$)" l)))))

(defn- lein-run
  []
  (let [{:keys [exit out]} (p/shell {:dir test-work-dir
                                     :err :out
                                     :out :string
                                     :continue true}
                                    "lein with-profile -user antq")]
    {:exit exit
     :out (parse-out out)}))

(defn- lein-scenario
  [opts]
  (lein-project opts)
  (lein-run))


(deftest detects-outdated-deps-test
  ;; test assumes clojure will always be at 1.x. probably a safe assumption!
  (is (match? {:exit 1
               :out [#"\| project\.clj +\| org\.clojure/clojure +\| 1\.10\.2 +\| 1"
                     "Available changes:"
                     #"- https://github.com/clojure/clojure/blob/clojure-1.*/changes\.md"]}
              (lein-scenario [:plugins [antq-coords]
                              :dependencies '[[org.clojure/clojure "1.10.2"]]]))))

(deftest detects-outdated-managed-deps-test
  ;; test assumes no further releases of libs, adjust accordingly if reality changes
  (is (match? {:exit 1
               :out [#"\| project\.clj +\| com\.stuartsierra/mapgraph +\| 0\.1\.0 +\| 0\.2\.1"
                     "Available changes:"
                     "- https://github.com/stuartsierra/mapgraph/blob/0.2.1/CHANGES.md"]}
              (lein-scenario [:plugins [antq-coords]
                              :managed-dependencies '[[com.stuartsierra/mapgraph "0.1.0"]]
                              :dependencies '[[com.stuartsierra/mapgraph]]]))))

(deftest detects-outdated-plugins-test
  ;; test assumes no further releases of libs, adjust accordingly if reality changes
  (is (match? {:exit 1
               :out [#"\| project\.clj +\| lein-swank/lein-swank +\| 1\.4\.1 +\| 1\.4\.5"]}
              (lein-scenario [:plugins [['lein-swank "1.4.1"]
                                        antq-coords]]))))

(deftest no-updates-test
  ;; test assumes no further releases of libs, adjust accordingly if reality changes
  (is (match? {:exit 0
               :out ["All dependencies are up-to-date."]}
              (lein-scenario [:managed-dependencies '[[com.stuartsierra/mapgraph "0.2.1"]]
                              :dependencies '[[me.raynes/fs "1.4.6"]
                                              [com.stuartsierra/mapgraph]]
                              :plugins [['lein-swank "1.4.5"]
                                        antq-coords]]))))

(deftest meta-exclude-test
  ;; test assumes no further releases of this lib, adjust accordingly
  (is (match? {:exit 1
               :out [#"| project\.clj +\| me.raynes/fs +\| 1\.4\.1 +\| 1\.4\.4 +\|"]}
              (lein-scenario [:plugins [antq-coords]
                              :dependencies [^{:antq/exclude ["1.4.6" "1.4.5"]} ['me.raynes/fs "1.4.1"]]]))))

(comment
  (recreate-work-dir)

  (lein-project [:dependencies [^{:antq/exclude ["1.4.6" "1.4.5"]} ['me.raynes/fs "1.4.1"]]])
  
  :eoc)
