(ns outdated
  (:require
   [helper.shell :as shell]))

(defn -main
  [& args]
  (apply shell/clojure {:continue true} "-M:outdated:nop" args))
