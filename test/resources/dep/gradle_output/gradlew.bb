;; A fake Gradle wrapper for antq.dep.gradle-test: prints what antq's init script would print
;; for a build with a nested subproject, together with lines that the build prints itself.
(let [args        (vec *command-line-args*)
      project-dir (second (drop-while #(not= "--project-dir" %) args))]
  (doseq [line ["Configuring the build (a line that the build prints itself)"
                "ANTQ_REPO;;settings-repo;https://settings.example.com/maven/"
                (str "ANTQ_PROJECT;:;" project-dir "/build.gradle")
                "ANTQ_REPO;:;root-repo;https://example.com/maven;jsessionid=1"
                (str "ANTQ_DEP;:;" project-dir "/build.gradle;org.example:root:1.0.0")
                (str "ANTQ_DEP;:;" project-dir "/build.gradle;org.example:root:1.0.0")
                (str "ANTQ_PROJECT;:tools;" project-dir "/tools/build.gradle")
                (str "ANTQ_PROJECT;:tools:cli;" project-dir "/tools/cli/build.gradle")
                "ANTQ_REPO;:tools:cli;settings-repo;https://override.example.com/maven/"
                (str "ANTQ_DEP;:tools:cli;" project-dir "/tools/cli/build.gradle;org.example:cli:2.0.0")]]
    (println line)))
