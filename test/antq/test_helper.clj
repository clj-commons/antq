(ns antq.test-helper
  (:require
   [antq.record :as r]
   [antq.upgrade.clojure]
   [antq.util.os :as os]
   [babashka.fs :as fs]
   [clojure.string :as str]
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

(defn os-path
  "Returns os-appropriate path as for unix-style path p"
  [p]
  (if (os/windows?)
    (str/replace p "/" "\\")
    p))

(defn setup-deps-scenario
  "Sets up an isolated test scenario in `target-dir` from files under test/resources/dep
  selected by `test-resources` which is a vector of vectors:

  ```clojure
  [[relative-source-name1 relative-dest-path1]
   [relative-source-name2]]
  ```
  Omit relative-dest-path if no rename is required.

  Returns a vector of all relative-dest-paths as string in OS-host syntax."
  [target-dir test-resources]
  (fs/delete-tree target-dir)
  (fs/create-dirs target-dir)
  (let [test-resources (mapv #(if (= 1 (count %))
                                [(first %) (first %)]
                                %)
                             test-resources)
        dest-paths (->> test-resources (mapv second) (map os-path) set)]
    (doseq [[source dest] test-resources]
      (let [dest (fs/path target-dir dest)
            source (fs/path "test/resources/dep" source)]
        (fs/create-dirs (fs/parent dest))
        (if (fs/directory? source)
          (fs/copy-tree source dest)
          (fs/copy source dest))))
    dest-paths))
