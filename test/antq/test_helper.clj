(ns antq.test-helper
  (:require
   [antq.record :as r]
   [antq.upgrade.clojure]
   [antq.util.os :as os]
   [babashka.fs :as fs]
   [clojure.string :as str]
   [lambdaisland.deep-diff2 :as ddiff]
   [org.httpkit.server :as http-server])
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

(defn- find-route
  "Return longest matching route for `uri` in `routes`."
  [routes uri]
  (->> routes
       (filter (fn [[path _]] (str/starts-with? uri path)))
       (sort-by (comp count key) >)
       first
       val))

(defn with-server
  "A simple little clojure/bb compatible HTTP server. Adjust as needed to support tests.

  `routes` is a map where key is route, ex, `\"/ok\"`, and value is a map with:
   - `:response-status`
   - `:response-headers`

  Calls `f` with map with `:base-url` and `:requests-atom` a vector of collected requests."
  [routes f]
  (let [requests-atom (atom [])
        handler (fn [req]
                  (swap! requests-atom conj
                         {:method (:request-method req)
                          :path (:uri req)
                          :auth (get-in req [:headers "authorization"])
                          :user-agent (get-in req [:headers "user-agent"])})
                  (if-let [{:keys [response-status response-headers]}
                           (find-route routes (:uri req))]
                    {:status (or response-status 200)
                     :headers response-headers}
                    {:status 404}))
        server (http-server/run-server handler {:ip "127.0.0.1" :port 0
                                                :legacy-return-value? false})]
    (try
      (f {:base-url (str "http://127.0.0.1:" (http-server/server-port server))
          :requests-atom requests-atom})
      (finally
        (http-server/server-stop! server)))))

(defmacro with-user-agent-property
  "Runs `body` with aether.connector.userAgent set to `value` (cleared when `value` is nil),
  then restores the original value."
  [value & body]
  `(let [old-value# (System/getProperty "aether.connector.userAgent")
         value# ~value]
     (try
       (if value#
         (System/setProperty "aether.connector.userAgent" value#)
         (System/clearProperty "aether.connector.userAgent"))
       ~@body
       (finally
         (if old-value#
           (System/setProperty "aether.connector.userAgent" old-value#)
           (System/clearProperty "aether.connector.userAgent"))))))
