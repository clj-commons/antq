(ns ^:no-doc antq.log
  (:require
   [antq.util.ansi :as ansi]
   [clojure.string :as str]))

(def ^:dynamic *verbose* false)

(defn- log
  "Use with-redefs on this fn to capture log events"
  [{:keys [level msg]}]
  (let [msg (->> (str/split-lines msg) (str/join "\n  "))]
    (binding [*out* *err*]
      (println (str (case level
                      :debug (str
                              (ansi/ctrl :bg-white :fg-white :dim) "["
                              (ansi/ctrl :fg-black :dim) "DEBUG"
                              (ansi/ctrl :fg-white :dim) "]"
                              (ansi/ctrl :off) " " msg)
                      :info  (str
                              (ansi/ctrl :bg-cyan :fg-cyan) "["
                              (ansi/ctrl :fg-white) "INFO"
                              (ansi/ctrl :fg-cyan) "]"
                              (ansi/ctrl :off) " " msg)
                      :warn  (str
                              (ansi/ctrl :bg-yellow :fg-yellow) "["
                              (ansi/ctrl :fg-black) "WARN"
                              (ansi/ctrl :fg-yellow) "]"
                              (ansi/ctrl :off) " " msg)
                      :error (str
                              (ansi/ctrl :bg-red :fg-red) "["
                              (ansi/ctrl :fg-white)
                              "ERROR"
                              (ansi/ctrl :fg-red) "]"
                              (ansi/ctrl :off) " " msg)))))))

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
