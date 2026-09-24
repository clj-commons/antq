(ns docker
  (:require
   [babashka.fs :as fs]
   [helper.shell :as shell]
   [lread.status-line :as status]))

(defn build
  [_opts]
  (status/line :head "Building docker image")
  (shell/command "docker build -t uochan/antq ."))

(defn sources
  [_opts]
  (status/line :head "Running antq against cwd from docker image")
  (shell/command "docker run --rm"
                 "--volume" (str (fs/cwd) ":/src")
                 "--workdir" "/src"
                 "uochan/antq:latest"))
