(ns ^:no-doc antq.report.edn
  (:require
   [antq.report :as report]))

(defmethod report/reporter "edn"
  [deps _options]
  (->> deps
       ;; Convert a record to just a map
       (map #(merge {} %))
       ;; NOTE Add diff-url for backward compatibility
       (map #(assoc % :diff-url (:changes-url %)))
       (pr-str)
       (println)))
