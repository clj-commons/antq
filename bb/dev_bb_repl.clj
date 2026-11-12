(ns dev-bb-repl
  (:require
   [babashka.classpath :as cp]
   [babashka.fs :as fs]
   [babashka.nrepl.server :as nrepl-server]
   [clojure.string :as str]
   [clojure.tools.build.api :as b]
   [lread.status-line :as status]))

(defn task
  {:org.babashka/cli {:spec {:host {:ref "<ADDR>"
                                    :alias :h
                                    :default "127.0.0.1"
                                    :desc "Host address"}
                             :port {:ref "<port>"
                                    :coerce :int
                                    :default 0
                                    :alias :p
                                    :desc "Port, 0 for auto-select"}}}}
  [opts]
  (status/line :head "Launching babashka nREPL")
  (let [basis (b/create-basis {:deps-dir "" :aliases [:dev :test]})]
    (cp/add-classpath (str/join (System/getProperty "path.separator")
                                (:classpath-roots basis)))
    (let [{:keys [port]} (nrepl-server/start-server! opts)]
      (when port
        (fs/delete-on-exit ".nrepl-port")
        (spit ".nrepl-port" port))
      @(promise))))
