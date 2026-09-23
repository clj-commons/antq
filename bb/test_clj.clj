(ns test-clj
  (:require
   [helper.clojure-versions :as clojure-versions]
   [helper.jdk :as jdk]
   [helper.shell :as shell]
   [lread.status-line :as status]))

(defn run-unit-tests
  [{:keys [mvn-version alias] :as _clojure-version} runner-args]
  (status/line :head (str "testing clojure source against clojure v" mvn-version))
  (apply shell/clojure (str "-M:dev" alias ":test") runner-args))

(def cli-clojure-versions (conj (mapv :version (clojure-versions/all)) "all"))

(def cli-spec
  (merge (clojure-versions/cli-opt cli-clojure-versions)
         {:kaocha-help {:desc "Get kaocha opts help"}}))

(def spec-cli-args
  (reduce (fn [acc [k v]]
            (let [{:keys [coerce alias]} v
                  coerce (if (= coerce nil) :boolean coerce)]
              (cond-> acc
                alias (assoc (str "-" (name alias)) coerce)
                :always (assoc (str "--" (name k)) coerce))))
          {}
          cli-spec))

(defn- strip-task-opts
  [args]
  ;; naive stripper naively handles :boolean and :string args, probably good enough
  (let [cli-arg-opts (set (keys spec-cli-args))]
    (loop [args args
           acc []]
      (if-let [arg (first args)]
        (if (cli-arg-opts arg)
          (recur (if (= :boolean (get spec-cli-args arg))
                   (-> args rest)
                   (-> args rest rest))
                 acc)
          (recur (rest args) (conj acc arg)))
        acc))))

(defn task
  {:org.babashka/cli {:spec cli-spec}}
  [{:keys [clojure-version kaocha-help]}]
  (let [env-jdk-version (jdk/version)
        clojure-versions (if (= "all" clojure-version)
                           (clojure-versions/all)
                           [(clojure-versions/lookup clojure-version)])]
    (if kaocha-help
      (shell/clojure "-M:dev:test --help")
      (let [runner-args (strip-task-opts *command-line-args*)]
        (doseq [v clojure-versions]
          (if (and (= "all" clojure-version)
                   (< (:major env-jdk-version) (:min-jdk-major v)))
            (status/line :warn "Skipping testing clojure version %s\nIt requires min JDK %s, found JDK %s"
                         (:mvn-version v) (:min-jdk-major v) (:version env-jdk-version))
            (run-unit-tests v runner-args)))))))
