(ns test-bb
  (:require
   [babashka.classpath :as cp]
   [clojure.string :as str]
   [clojure.tools.build.api :as b]
   [helper.legacy :as legacy]
   [lread.status-line :as status]))

(defn task
  {:org.babashka/cli {:restrict false :restrict-args false ; allow test runner opts through
                      :spec {:runner-help {:desc "Get test runner opts help"}}}}
  [{:keys [runner-help]}]
  (legacy/check-for-build-remnants)
  (let [basis (b/create-basis {:deps-dir "" :aliases [:dev :test]})]
    (cp/add-classpath (str/join (System/getProperty "path.separator")
                                (:classpath-roots basis)))
    (if runner-help
      ((requiring-resolve 'cognitect.test-runner/-main) "--test-help")
      (do
        (status/line :head "testing clojure source against babashka")
        (apply (requiring-resolve 'cognitect.test-runner/-main)
               *command-line-args*)))))
