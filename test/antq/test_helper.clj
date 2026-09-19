(ns antq.test-helper
  (:require
   [antq.record :as r]
   [antq.upgrade.clojure]
   [lambdaisland.deep-diff2 :as ddiff])
  (:import
   java.io.File
   lambdaisland.deep_diff2.diff_impl.Mismatch))

(defn test-dep
  [m]
  (r/map->Dependency (merge {:type :test} m)))

(defn name-version-sorted-list
  [deps]
  (->> deps
       (map #(let [url (get-in % [:extra :url])
                   sha (get-in % [:extra :sha])]
               (cond-> (select-keys % [:name :version])
                 url (assoc :url url)
                 sha (assoc :sha sha))))
       (sort-by #(str (:name %) (:version %)))))

(defn diff-deps
  [expected-deps actual-deps]
  (->> (ddiff/diff (name-version-sorted-list expected-deps)
                   (name-version-sorted-list actual-deps))
       (filter #(instance? Mismatch (:version %)))
       ;; convert `:version` to a simple map
       (map #(cond-> %
               (contains? % :version)
               (update :version (fn [m] (merge {} m)))

               (contains? % :sha)
               (update :sha (fn [m] (merge {} m)))))
       (set)))

(defn diff-lines
  [expected-lines actual-lines]
  (->> (ddiff/diff expected-lines
                   actual-lines)
       (filter #(instance? Mismatch %))
       (map #(select-keys % [:- :+]))
       (set)))

(defmacro with-temp-file
  [[sym content] & body]
  `(let [~sym (File/createTempFile "tmp" "tmp")]
     (try
       (spit ~sym ~content)
       ~@body
       (finally
         (.delete ~sym)))))
