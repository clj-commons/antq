(ns dev-repl
  (:require
   [babashka.process :as process]
   [helper.clojure-versions :as clojure-versions]
   [lread.status-line :as status]))

(defn task
  {:org.babashka/cli {:spec {:host {:ref "<ADDR>"
                                    :alias :h
                                    :default "127.0.0.1"
                                    :desc "Host address"}
                             :bind {:ref "<ADDR>"
                                    :alias :b
                                    :default "127.0.0.1"
                                    :desc "Bind address"}
                             :port {:ref "<symbols>"
                                    :coerce :int
                                    :default 0
                                    :alias :p
                                    :desc "Port, 0 for auto-select"}}}}
  [{:keys [host bind port]}]
  (status/line :head "Launching Clojure nREPL")
  (process/exec "clj" (str "-M:" (:alias (clojure-versions/current-prod)) ":dev:test:nrepl/jvm")
                "-h" host
                "-b" bind
                "-p" port))
