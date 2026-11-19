;; A fake Gradle wrapper for antq.dep.gradle-test: fails like a broken build.
(require '[clojure.string :as str])

(when (str/includes? (str/join " " *command-line-args*) "antqDependencies")
  (binding [*out* *err*]
    (println "FAILURE: Build failed with an exception.")
    (println "* What went wrong:")
    (println "A problem occurred evaluating root project 'gradle_failure'."))
  (System/exit 1))
