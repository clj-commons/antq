(require '[babashka.fs :as fs])

(let [args        (vec *command-line-args*)
      opt         (fn [flag] (second (drop-while #(not= flag %) args)))
      project-dir (opt "--project-dir")
      init-script (opt "--init-script")
      task        (some #{"antqDependencies"} args)]
  (when-not (and task init-script (fs/regular-file? init-script))
    (binding [*out* *err*]
      (println "Expected the antqDependencies task and an existing init script"))
    (System/exit 1))
  (println (str "ANTQ_PROJECT;:;" project-dir "/build.gradle"))
  (println "ANTQ_REPO;:;wrapper-repo;https://example.com/maven/")
  (println (str "ANTQ_DEP;:;" project-dir "/build.gradle;org.example:from-wrapper:1.0.0")))
