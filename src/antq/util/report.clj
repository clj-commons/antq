(ns ^:no-doc antq.util.report
  (:require
   [antq.record :as r]))

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
