(ns test-bb-runner
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]
   [clojure.test :as t]))

(def ^:private jvm-only
  '#{antq.lein-plugin-test
     antq.util.aether-test})

(defn- test-namespaces
  []
  (->> (fs/glob "test" "**_test.clj")
       (map #(-> (str (fs/relativize "test" %))
                 (str/replace #"\.clj$" "")
                 (str/replace fs/file-separator ".")
                 (str/replace "_" "-")
                 symbol))
       (remove jvm-only)
       sort))

(defn -main
  [& _]
  (let [namespaces (test-namespaces)]
    (apply require namespaces)
    (let [{:keys [fail error]} (apply t/run-tests namespaces)]
      (System/exit (if (zero? (+ fail error)) 0 1)))))
