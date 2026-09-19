(ns ^:no-doc antq.report.format
  (:require
   [antq.report :as report]
   [antq.util.dep :as u.dep]
   [antq.util.ver :as u.ver]
   [pogonos.core :as pg]))

(def ^:private default-outdated-message-format
  "{{name}} {{version}} is outdated. Latest version is {{latest-version}}. {{changes-url}}")

(def ^:private default-unverified-group-name-message-format
  "{{name}} will be unverified. Please consider using {{latest-name}}.")

(def ^:private default-failed-message-format
  "Failed to fetch the latest version of {{name}} {{version}}.")

(defn apply-format-string
  [dep format-string]
  (let [dep (-> dep
                (assoc :latest-version (u.ver/normalize-latest-version dep)
                       ;; NOTE Add diff-url for backward compatibility
                       :diff-url (:changes-url dep))
                (select-keys [:file :name :version :latest-version :message :diff-url :changes-url :latest-name]))]
    (pg/render-string format-string dep)))

(defmethod report/reporter "format"
  [deps options]
  (let [format-string (:error-format options)]
    (when (seq deps)
      (doseq [s (->> deps
                     (sort u.dep/compare-deps)
                     ;; default message
                     (map #(assoc % :message
                                  (cond
                                    (:latest-version %)
                                    (apply-format-string % default-outdated-message-format)

                                    (:latest-name %)
                                    (apply-format-string % default-unverified-group-name-message-format)

                                    :else
                                    (apply-format-string % default-failed-message-format))))
                     (map #(apply-format-string % format-string)))]
        (println s)))))
