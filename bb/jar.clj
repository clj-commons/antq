(ns jar
  (:require
   [clean]
   [helper.shell :as shell]
   [lread.status-line :as status]))

(defn- build
  [op]
  (clean/task {})
  (status/line :head "Performing: %s" op)
  (shell/clojure "-T:build" op))

(defn jar
  [_opts]
  (build "jar"))

(defn uberjar
  [_opts]
  (build "uberjar"))

(defn install
  [_opts]
  (build "install"))
