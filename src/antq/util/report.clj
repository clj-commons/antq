(ns ^:no-doc antq.util.report
  (:require
   [antq.record :as r])
  (:import
   (java.time
    Duration
    Instant)))

(defn age
  "Returns how long ago the ISO-8601 `date` was, e.g. `14h` or `3d`."
  [date]
  (let [hours (max 0 (.toHours (Duration/between (Instant/parse date) (Instant/now))))]
    (if (< hours 24)
      (str hours "h")
      (str (quot hours 24) "d"))))

(defn cooldown-days
  "Returns the number of days of a cooldown like `7d`, or nil if `cooldown` isn't one.
  Days are the only unit for now."
  [cooldown]
  (when (string? cooldown)
    (some-> (re-matches #"(\d+)d" cooldown) second parse-long)))

(defn skip-duplicated-file-name
  {:malli/schema [:=> [:cat r/?dependencies] r/?dependencies]}
  [sorted-deps]
  (loop [[dep & rest-deps] sorted-deps
         last-file nil
         result []]
    (if-not dep
      result
      (if (or (= "" (:file dep))
              (= last-file (:file dep)))
        (recur rest-deps last-file (conj result (assoc dep :file "")))
        (recur rest-deps (:file dep) (conj result dep))))))
