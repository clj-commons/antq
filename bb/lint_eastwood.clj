(ns lint-eastwood
  (:require
   [helper.shell :as shell]
   [lread.status-line :as status]))

(defn task
  [_opts]
  (status/line :head "eastwood: linting")
  (shell/command "clojure -M:dev:eastwood"))
