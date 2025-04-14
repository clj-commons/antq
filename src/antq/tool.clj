(ns ^:no-doc antq.tool
  (:require
   [antq.cli :as cli]
   [antq.core :as core]
   [antq.log :as log]))

(defn outdated
  ;; docstring is presented as part of help for tool
  ;; clojure -A:deps -Tantq help/doc, but we also generate dynamic usage help
  ;; via `help` below and via `:help true` are if passed in here
  "Point out outdated dependencies.

  Options:
  - :exclude             <array of string>
  - :focus               <array of string>
  - :skip                <array of string>
  - :error-format        <string>
  - :reporter            <string>
  - :directory           <array of string>
  - :upgrade             <boolean>
  - :verbose             <boolean>
  - :force               <boolean>
  - :download            <boolean>
  - :ignore-locals       <boolean>
  - :check-clojure-tools <boolean>
  - :no-diff             <boolean>
  - :changes-in-table    <boolean>
  - :transitive          <boolean>
  - :help                <boolean>"
  [options]
  (let [options (cli/validate-tool-opts options)]
    (binding [log/*verbose* (:verbose options false)]
      (with-redefs [core/system-exit (fn [n]
                                       (when (not= 0 n)
                                         (throw (ex-info "Exited" {:code n})))
                                       n)]
        (core/main* options)))))

(defn help
  [& _]
  (log/info (cli/usage-help {:opts {:usage-help-style :clojure-tool}})))
