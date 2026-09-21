(ns lint-cljstyle
  (:require
   [helper.shell :as shell]
   [lread.status-line :as status]))

(defn -main
  [& args]
  (status/line :head "cljstyle: linting")
  (let [{:keys [exit]} (apply shell/clojure {:continue true} "-M:cljstyle" args)]
    (if (zero? exit)
      (status/line :detail "Success")
      (System/exit exit))))
