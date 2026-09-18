(ns ^:no-doc leiningen.antq
  "Entry point antq when used as a leiningen plugin"
  (:require
   [clojure.edn :as edn]
   [leiningen.core.eval :as lein-eval]
   [leiningen.core.main :as lein-main])
  (:import [java.io File]))
 
(defn antq
  ;; docstring is presented as help for plugin
  ;; lein antq --help
  "Leiningen plugin.

  Checks project.clj via full Leiningen evaluation. Does not check any other sources (deps.edn, etc).

  For the time being it merely checks for outdated dependencies;
  it doesn't support the `:upgrade` option because it cannot always know what to fix
  (in face of eval, profiles, plugins/middleware)."
  [{:keys [dependencies managed-dependencies plugins repositories antq] :as project}]
  (let [antq-plugin-version (->> plugins
                                 (filter (fn [dep] (= (first dep) 'com.github.liquidz/antq)))
                                 first
                                 second)
        deps (->> dependencies
                  (into managed-dependencies)
                  (into plugins)
                  distinct)
        isolated-project {:dependencies [['com.github.liquidz/antq antq-plugin-version]]
                          :debug true}
        result-file (File/createTempFile "antq-lein-plugin-result" ".edn")]
    (.deleteOnExit result-file)
    ;; we delegate to an isolated sub-process to avoid classpath issues with Maven deps
    ;; coming from lein and tools.deps
    (lein-eval/eval-in-project
      isolated-project
      `(do
         (antq.impl.lein-plugin/antq ~(str result-file)
                                     {:dependencies '~deps :repositories ~repositories}
                                     ~antq)
         (shutdown-agents))
      `(require '[antq.impl.lein-plugin]))
    (let [{:keys [exit]} (-> (slurp result-file) edn/read-string)]
      (binding [lein-main/*exit-process?* true]
        (lein-main/exit exit)))))

