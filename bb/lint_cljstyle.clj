(ns lint-cljstyle
  (:require
   [helper.shell :as shell]
   [lread.status-line :as status]))

(defn -main
  [& args]
  (status/line :head "cljstyle: linting")
  (apply shell/clojure "-M:cljstyle" args))
