(ns ^:no-doc antq.log)

(def ^:dynamic *verbose* false)

(defn info
  [s]
  (println s))

(defn warning
  [s]
  (when *verbose*
    (binding [*out* *err*]
      (println s))))

(defn error
  [s]
  (binding [*out* *err*]
    (println s)))

(def ^:private print-lock (Object.))

(defn async-print
  [s]
  (locking print-lock
    (print s)
    (flush)))
