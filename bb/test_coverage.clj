(ns test-coverage
  (:require
   [helper.legacy :as legacy]
   [helper.shell :as shell]
   [lread.status-line :as status]))

(defn task
  [_opts]
  (legacy/check-for-build-remnants)
  (status/line :head "Generating test coverage reports")
  (shell/clojure "-M:dev:test --skip-meta integration --plugin cloverage --codecov --cov-ns-exclude-regex leiningen.antq"))
