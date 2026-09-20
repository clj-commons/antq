(ns ^:no-doc antq.log)

(def ^:dynamic *verbose* false)

(defn info
  {:malli/schema [:=> [:cat 'string?] 'nil?]}
  [s]
  (println s))

(defn warning
  {:malli/schema [:=> [:cat 'string?] 'nil?]}
  [s]
  (when *verbose*
    (binding [*out* *err*]
      (println s))))

(defn error
  {:malli/schema [:=> [:cat 'string?] 'nil?]}
  [s]
  (binding [*out* *err*]
    (println s)))

(def ^:private print-lock (Object.))

(defn async-print
  {:malli/schema [:=> [:cat 'string?] 'nil?]}
  [s]
  (locking print-lock
    (print s)
    (flush)))
