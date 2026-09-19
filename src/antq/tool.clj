(ns ^:no-doc antq.tool
  (:require
   [antq.core :as core]
   [antq.log :as log]
   [clojure.set :as set]
   [clojure.tools.cli :as cli]))

(defn prepare-options
  [options]
  (let [default-options (:options (cli/parse-opts [] core/cli-options))
        options (or options {})
        additional-keys (set/difference (set (keys options))
                                        (set (keys default-options)))]
    (->> default-options
         (reduce-kv
          (fn [accm k v]
            (let [opt-value (get options k)]
              (cond
                (and (sequential? v)
                     (sequential? opt-value))
                (assoc accm k (map str (concat v opt-value)))

                (seq opt-value)
                (assoc accm k (str opt-value))

                :else
                (assoc accm k v))))

          {})
         (merge (select-keys options additional-keys)))))

(defn outdated
  ;; docstring is presented as part of help for tool
  ;; clojure -A:deps -Tantq help/doc
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
  - :transitive          <boolean>"
  [& [options]]
  (let [options (prepare-options options)]
    (binding [log/*verbose* (:verbose options false)]
      (with-redefs [core/system-exit (fn [n]
                                       (when (not= 0 n)
                                         (throw (ex-info "Exited" {:code n})))
                                       n)]
        (core/main* options nil)))))

(defn help
  [& _]
  (println "Use `clojure -A:deps -Tantq help/doc` instead"))
