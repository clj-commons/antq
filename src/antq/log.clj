(ns ^:no-doc antq.log
  (:require
   [clojure.string :as str]))

(def ^:dynamic *verbose* false)

(def ^:private ansi-codes
  {:fg-black 30
   :fg-white 37
   :fg-cyan 36
   :fg-yellow 33
   :bg-white 47
   :bg-cyan 46
   :bg-yellow 43
   :bg-red 41
   :fg-red 31
   :dim 2
   :off 0})

(defn- ansi
  [& codes]
  (str "\u001b["
       (str/join ";"
                 (mapv #(or (get ansi-codes %)
                            (throw (ex-info (str "invalid code: " %) {})))
                       codes))
       "m"))

(defn- log
  "Use with-redefs on this fn to capture log events"
  [{:keys [level msg]}]
  (let [msg (->> (str/split-lines msg) (str/join "\n  "))]
    (binding [*out* *err*]
      (println (str (case level
                      :debug (str (ansi :bg-white :fg-black :dim) " DEBUG " (ansi :off) " " msg)
                      :info  (str (ansi :bg-cyan :fg-white) " INFO " (ansi :off) " " msg)
                      :warn  (str (ansi :bg-yellow :fg-black) " WARN " (ansi :off) " " msg)
                      :error (str (ansi :bg-red :fg-white) " ERROR " (ansi :off) " " msg)))))))

(defn debug
  [s]
  (when *verbose*
    (log {:level :debug :msg s})))

(defn info
  [s]
  (log {:level :info :msg s}))

(defn warn
  [s]
  (log {:level :warn :msg s}))

(defn error
  [s]
  (log {:level :error :msg s}))

(def ^:private print-lock (Object.))

(defn async-print
  [s]
  (locking print-lock
    (.print ^java.io.PrintWriter *err* s)
    (.flush ^java.io.PrintWriter *err*)))

(comment
  (do
    (println "\n")
    (doseq [l [:debug :info :warn :error]]
      (log {:level l :msg "Testing 123\nFoo\nBar"})))

  :eoc)
