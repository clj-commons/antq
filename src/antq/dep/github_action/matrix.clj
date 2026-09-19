(ns ^:no-doc antq.dep.github-action.matrix
  (:require
   [antq.util.dep :as u.dep]))

(defn- matrix-variable-name
  [version]
  (when-let [[[_ vname]] (re-seq #"\$\{\{\s*matrix\.(.+?)\s*}}"
                                 (str version))]
    vname))

(defn expand-matrix-value
  [parsed-yaml job-name deps]
  (mapcat
   (fn [dep]
     (if-let [vname (some-> (:version dep)
                            (matrix-variable-name))]
       (let [values (-> (:jobs parsed-yaml)
                        (get-in  [job-name :strategy :matrix (keyword vname)]))]
         (map #(u.dep/normalize-version-by-name
                (assoc dep
                       :version %
                       :only-newest-version? true))
              values))
       [dep]))
   deps))
