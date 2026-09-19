(ns ^:no-doc antq.ver
  (:require
   [clojure.string :as str]
   [version-clj.core :as version]))

(def ^:private under-development-keywords
  #{"alpha" "beta" "rc"})

(defn under-development?
  [s]
  (if-let [l (and s
                  (string? s)
                  (str/lower-case s))]
    (some? (some #(str/includes? l %) under-development-keywords))
    false))

(defn snapshot?
  [s]
  (if (and s
           (string? s))
    (str/includes? (str/lower-case s) "snapshot")
    false))

(defmulti get-sorted-versions
  (fn [dep _options]
    (:type dep)))
(defmethod get-sorted-versions :default
  [dep _]
  (throw (ex-info "Unknown dependency type" dep)))

(defmulti latest? :type)
(defmethod latest? :default
  [dep]
  (and (:version dep)
       (:latest-version dep)
       (string? (:version dep))
       (string? (:latest-version dep))
       (<= 0  (version/version-compare
               (:version dep)
               (:latest-version dep)))))
