(ns test-coverage
  (:require
   [helper.shell :as shell]
   [lread.status-line :as status]))

(defn task
  [_opts]
  (status/line :head "Generating test coverage reports")
  (shell/clojure "-M:dev:test --skip-meta integration --plugin cloverage --codecov --cov-ns-exclude-regex leiningen.antq"))
