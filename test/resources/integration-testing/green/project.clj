(defproject green "n/a"
  :description "Please keep me updated - `lein antq` should pass for this project"
  :managed-dependencies [[com.stuartsierra/dependency "1.0.0"]]
  :dependencies [[org.clojure/clojure "1.12.6"]
                 [com.stuartsierra/dependency]]
  :plugins [[lein-pprint "1.3.2"]
            [com.github.liquidz/antq "RELEASE"]]
  :antq {:exclude ["nrepl/nrepl"]})
