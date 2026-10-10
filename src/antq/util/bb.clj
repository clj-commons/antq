(ns ^:no-doc antq.util.bb)

(def ^:private bb-version
  (System/getProperty "babashka.version"))

(defmacro if-bb
  "Expands to then on babashka and to else on the JVM."
  [then else]
  (if bb-version then else))
