(ns ^:no-doc leiningen.antq
  (:require
   [antq.core]
   [antq.dep.leiningen :as dep.lein]
   [antq.log :as log]
   [antq.record :as r]
   [antq.report :as report]
   [leiningen.core.main]))

(defn antq
  ;; docstring is presented as help for plugin
  ;; lein antq --help
  "Leiningen plugin.

  Checks project.clj via full Leiningen evaluation. Does not check any other sources (deps.edn, etc).

  For the time being it merely checks for outdated dependencies;
  it doesn't support the `:upgrade` option because it cannot always know what to fix
  (in face of eval, profiles, plugins/middleware)."
  [{:keys [dependencies managed-dependencies plugins repositories]
    {:keys [error-format reporter upgrade] :as antq-options} :antq}]
  (let [repos (dep.lein/normalize-repositories repositories)
        options (cond-> antq-options
                  (and (not error-format)
                       (not reporter)) (assoc :reporter "table"))
        _ (when upgrade
            (assert false ":upgrade option not supported under the Lein plugin."))
        alog (log/start-async-logger!)]
    (try
      (let [outdated (->> dependencies
                          (into managed-dependencies)
                          (into plugins)
                          (distinct)
                          (keep (fn [[dep-name version :as dep]]
                                  (when (dep.lein/acceptable-version? version)
                                    (r/map->Dependency {:project :leiningen
                                                        :type :java
                                                        :file "project.clj"
                                                        :name (dep.lein/normalize-name dep-name)
                                                        :version version
                                                        :repositories repos
                                                        :exclude-versions (seq (dep.lein/exclude-version-range dep))}))))
                          (antq.core/antq options))]

        (report/reporter outdated options)
        (binding [leiningen.core.main/*exit-process?* true]
          (leiningen.core.main/exit (if (seq outdated)
                                      1
                                      0))))
      (finally
        (log/stop-async-logger! alog)))))
