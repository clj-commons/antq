(ns ^:no-doc antq.ver
  (:require
   [antq.util.ver :as u.ver]
   [clojure.string :as str]
   [version-clj.core :as version]))

(def ^:private under-development-keywords
  #{"alpha" "beta" "rc" "cr" "m" "milestone" "dev" "pr" "pre" "prealpha" "preview"
    "experimental" "unstable"})

(defn under-development-word?
  "`alpha` and `beta` may carry one more letter, e.g. `alphaB`, but not `alphabet`."
  [word]
  (or (contains? under-development-keywords word)
      (and (= 6 (count word)) (str/starts-with? word "alpha"))
      (and (= 5 (count word)) (str/starts-with? word "beta"))))

(defn under-development-segment?
  "The one-letter `m` must not follow a digit: `1.1.1m` is an OpenSSL letter release.
  e.g. \"M7\" => true, \"rc2\" => true, \"march\" => false, \"1m\" => false"
  [segment]
  (boolean
   (some (fn [{:keys [digit-before? word]}]
           (and (under-development-word? word)
                (not (and digit-before? (= "m" word)))))
         (u.ver/segment-words segment))))

(defn under-development?
  [s]
  (if (and s
           (string? s))
    (->> (u.ver/segments (u.ver/remove-build-metadata s))
         (some under-development-segment?)
         (boolean))
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
