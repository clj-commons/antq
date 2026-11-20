(ns antq.min-clojure-version-test
  "Checks on our min clojure version"
  (:require
   [babashka.fs :as fs]
   [babashka.process :as p]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.test :as t]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test]))

(t/deftest ^:lint fails-fast-on-unsupported-clojure-version-test
  (doseq [version ["1.10.3" "1.11.3"]]
    (t/is (match? {:exit 1
                   :err (m/via str/trim #"antq requires Clojure 1.11.4 or later.")}
                  (p/shell {:continue true :err :string}
                           "clojure -Sdeps"
                           (pr-str {:deps {'org.clojure/clojure {:mvn/version version}}})
                           "-M" "-m" "antq.core" "--help"))
          (str "version: " version))))

(t/deftest ^:lint ensure-min-clojure-version-in-sync-test
  ;; if these tests fails, we've updated min clojure version without updating check, tests, & docs
  (let [expected "1.11.4" ; <- update to match deps.edn
        min-clojure-version (-> "deps.edn"
                                slurp
                                edn/read-string
                                :deps
                                (get 'org.clojure/clojure)
                                :mvn/version)]

    (t/is (match? expected min-clojure-version)
          "check in sync")
    (let [docs (keep (fn [f]
                       (when-let [ref-version (->> f
                                                   fs/slurp
                                                   (re-find #"(?m)^:min-clojure-version: (.*)$")
                                                   last)]
                         {:doc f :min-clojure-version ref-version}))
                     (fs/glob "." "**.adoc"))]
      (t/is (> (count docs) 0) "some docs ref min clojure version")
      (doseq [{:keys [doc min-clojure-version]} docs]
        (t/is (= expected min-clojure-version)
              (str "doc: " doc))))))
