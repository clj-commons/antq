(ns test-bb
  (:require
   [babashka.fs :as fs]
   [babashka.process :as p]
   [clojure.string :as str]
   [helper.shell :as shell]
   [lread.status-line :as status]))

(defn task
  [_]
  (status/line :head "testing clojure source against babashka")
  (let [classpath (-> (p/shell {:out :string} "clojure" "-Spath" "-A:dev") :out str/trim)]
    (shell/command "bb" "--classpath" (str classpath fs/path-separator "bb")
                   "-m" "test-bb-runner")))
