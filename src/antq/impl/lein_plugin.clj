(ns ^:no-doc antq.impl.lein-plugin
  "Called from isolated project from leiningen.antq to avoid classpath issues with conflicting
  Maven deps. Runs in a separate process."
  (:require
   [antq.core]
   [antq.dep.leiningen :as dep.lein]
   [antq.record :as r]
   [antq.report :as report]))

(defn antq
  [result-file
   {:keys [dependencies repositories] :as _lein-project}
   {:keys [error-format reporter upgrade] :as antq-options}]
  (let [repos (dep.lein/normalize-repositories repositories)
        ;; reconstitute metadata
        dependencies (mapv (fn [{:keys [dep mdata]}]
                             (with-meta dep mdata))
                           dependencies)
        options (cond-> antq-options
                  (and (not error-format)
                       (not reporter)) (assoc :reporter "table"))
        _ (when upgrade
            (assert false ":upgrade option not supported under the Lein plugin."))
        outdated (->> dependencies
                      (keep (fn [dep]
                              (let [[dep-name version] dep]
                                (when (dep.lein/acceptable-version? version)
                                  (r/map->Dependency {:project :leiningen
                                                      :type :java
                                                      :file "project.clj"
                                                      :name (dep.lein/normalize-name dep-name)
                                                      :version version
                                                      :repositories repos
                                                      :exclude-versions (seq (dep.lein/exclude-version-range dep))})))))
                      (antq.core/antq options))]
    (report/reporter outdated options)
    (spit result-file (pr-str {:exit (if (seq outdated) 1 0)}))))
