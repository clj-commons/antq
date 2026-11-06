(ns install
  (:require
   [build-config :as build-config]
   [clojure.tools.build.api :as b]
   [jar :as jar]
   [lread.status-line :as status]))

(defn task
  {:org.babashka/cli {:spec {:version {:desc "optional version override for testing"}}}}
  [opts]
  (let [{:keys! [version class-dir lib basis jar-file]}
        (-> (build-config/config opts) jar/jar)]
    (status/line :head "Install to local maven repo")
    (status/line :detail "installing: %s" jar-file)
    (b/install {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :jar-file jar-file})))
