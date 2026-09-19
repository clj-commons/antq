(ns ^:no-doc antq.report.json
  (:require
   [antq.report :as report]
   [clojure.data.json :as json]))

(defmethod report/reporter "json"
  [deps _options]
  (->> deps
       ;; NOTE Add diff-url for backward compatibility
       (map #(assoc % :diff-url (:changes-url %)))
       (json/write-str)
       (println)))
