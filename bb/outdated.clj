(ns outdated
  (:require
   [helper.shell :as shell]
   [lread.status-line :as status]))

(defn -main
  [& args]
  (status/line :head "Checking Clojure deps")
  (apply shell/command {:continue true} "clojure -M:outdated:nop" args))
