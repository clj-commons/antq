(ns ^:no-doc antq.report.table
  (:require
   [antq.log :as log]
   [antq.report :as report]
   [antq.util.dep :as u.dep]
   [antq.util.file :as u.file]
   [antq.util.report :as u.report]
   [antq.util.ver :as u.ver]
   [clojure.set :as set]
   [clojure.string :as str]))

(def ^:private progress (atom nil))

(defn- expand-children
  [parent-grouped-deps parent-deps]
  (->> parent-deps
       (sort u.dep/compare-deps)
       (mapcat (fn [parent-dep]
                 (->> (or (get parent-grouped-deps (:name parent-dep))
                          [])
                      (map #(assoc % :level (inc (:level parent-dep))))
                      (expand-children parent-grouped-deps)
                      (concat [parent-dep]))))))

(defn- calc-max-length
  [column-name deps]
  (apply max (count (str column-name)) (map (comp count column-name) deps)))

(defn- generate-row
  [dep columns max-lengths]
  (->> columns
       (map-indexed (fn [i column]
                      (format (str "%-" (nth max-lengths i) "s")
                              (or (get dep column)
                                  ""))))
       (str/join " | ")
       (format "| %s |")))

(defn- apply-level
  [level s]
  (let [indent (apply str (repeat (* level 2) " "))]
    (str (when (seq indent)
           (str indent))
         s)))

(defn- print-table
  [options deps]
  (let [columns (cond-> [:file :name :current :latest]
                  (:changes-in-table options)
                  (conj :changes-url))
        max-lengths (map #(calc-max-length % deps) columns)]
    (println (generate-row (->> columns
                                (map #(vector % (str %)))
                                (into {}))
                           columns max-lengths))
    (println (->> max-lengths
                  (map #(apply str (repeat % "-")))
                  (str/join "-|-")
                  (format "|-%s-|")))
    (doseq [dep deps]
      (println (generate-row dep columns max-lengths)))))

(defmethod report/reporter "table"
  [deps options]
  ;; Show table
  (if (empty? deps)
    (println "All dependencies are up-to-date.")
    (let [parent-grouped-deps (group-by :parent deps)]
      (->> (or (get parent-grouped-deps nil)
               [])
           (map #(assoc % :level 0))
           (expand-children parent-grouped-deps)
           (u.report/skip-duplicated-file-name)
           (map #(assoc % :latest-version (u.ver/normalize-latest-version %)))
           (map #(update % :file u.file/normalize-path))
           (map #(let [latest-key (if (seq (:latest-name %))
                                    :latest-name
                                    :latest-version)]
                   (set/rename-keys % {:version :current
                                       latest-key :latest})))
           (map #(update % :name (partial apply-level (or (:level %) 0))))
           (print-table options))))

  ;; Show changes URLs
  (when-not (:changes-in-table options)
    (let [urls (->> deps
                    (filter :latest-version)
                    (sort u.dep/compare-deps)
                    (keep :changes-url)
                    (distinct))]
      (when (seq urls)
        (println "\nAvailable changes:")
        (doseq [u urls]
          (println "-" u))))))

(defn- progress-text
  [{:keys [width total-count current-count]}]
  (let [width (or width 50)
        ratio (int (* width (/ current-count total-count)))]
    (format "[%s%s] %d/%d\r"
            (apply str (repeat ratio "#"))
            (apply str (repeat (- width ratio) "-"))
            current-count
            total-count)))

(defmethod report/init-progress "table"
  [deps _options]
  (reset! progress {:total-count (count deps)
                    :count-atom (atom 0)}))

(defmethod report/run-progress "table"
  [_dep _options]
  (when-let [{:keys [total-count count-atom]} @progress]
    (when (< @count-atom total-count)
      (swap! count-atom inc)
      (log/async-print
       (progress-text {:total-count total-count :current-count @count-atom})))))

(defmethod report/deinit-progress "table"
  [_ _]
  (println ""))
