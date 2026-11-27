(ns ^:no-doc antq.util.ansi
  (:require
   [antq.util.env :as u.env]
   [clojure.string :as str]))

(def ^:dynamic *no-color* false)

(def ^:private ansi-codes
  ;; add more as needed
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

(defn ctrl
  "Returns ctrl text for `codes`, returns empty string if color is disabled."
  [& codes]
  (if (or *no-color* (seq (u.env/getenv "NO_COLOR")))
    ""
    (str "\u001b["
         (str/join ";"
                   (mapv #(or (get ansi-codes %)
                              (throw (ex-info (str "invalid code: " %) {})))
                         codes))
         "m")))
