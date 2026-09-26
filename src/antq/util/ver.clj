(ns ^:no-doc antq.util.ver
  (:require
   [antq.util.exception :as u.ex]
   [clojure.string :as str]))

(def ^:private no-latest-version-error
  "Failed to fetch")

(def ^:private timed-out-error
  "Timed out")

(defn remove-qualifiers
  "c.f. https://github.com/xsc/version-clj/blob/v2.0.2/src/version_clj/qualifiers.cljc"
  [s]
  (str/replace s #"[\-.](alpha|beta|milestone|rc|snapshot|final|stable)\d*" ""))

(defn normalize-version
  [s]
  (str/replace s #"^[^\d]+" ""))

(defn sem-ver?
  [s]
  (some? (re-find #"^\d+(\.\d+)*$" s)))

(defn remove-build-metadata
  "Build metadata has no precedence, c.f. https://semver.org/#spec-item-10
  e.g. \"1.2.3+alpha.1\" => \"1.2.3\""
  [s]
  (if-let [i (str/index-of s "+")]
    (subs s 0 i)
    s))

(defn segments
  "e.g. \"2.4-M7-groovy-5.0\" => (\"2\" \"4\" \"M7\" \"groovy\" \"5\" \"0\")"
  [s]
  (remove str/blank? (str/split s #"[-._]")))

(defn- digit?
  [c]
  (Character/isDigit (char c)))

(defn segment-words
  "The runs of letters in a segment, and whether a digit precedes each run.
  e.g. \"M7\" => ({:digit-before? false :word \"m\"})
       \"R8RC2\" => ({:digit-before? false :word \"r\"} {:digit-before? true :word \"rc\"})"
  [segment]
  (->> (partition-by digit? segment)
       (map (partial apply str))
       (cons nil)
       (partition 2 1)
       (remove (fn [[_ run]] (digit? (first run))))
       (map (fn [[before run]]
              {:digit-before? (some? before)
               :word (str/lower-case run)}))))

(defmulti normalize-latest-version
  (fn [dep] (:type dep)))

(defmethod normalize-latest-version :default
  [{:keys [latest-version]}]
  (cond
    (string? latest-version)
    latest-version

    (u.ex/ex-timeout? latest-version)
    timed-out-error

    :else
    no-latest-version-error))

(defmethod normalize-latest-version :git-sha
  [{:keys [version latest-version]}]
  (cond
    (and version
         latest-version
         (string? version)
         (string? latest-version))
    (subs latest-version 0 (count version))

    (u.ex/ex-timeout? latest-version)
    timed-out-error

    :else
    no-latest-version-error))

(defn in-range?
  "e.g. '1.x' matches '1.0.0', '1.1.0' and so on.

  Notations are based on package.json.
  cf. https://docs.npmjs.com/cli/v10/configuring-npm/package-json#dependencies"
  [version-range target-version]
  (let [re (-> version-range
               ;; escape chars
               (str/replace "." "\\.")
               (str/replace "+" "\\+")
               (str/replace "?" "\\?")
               ;; .x
               (str/replace "\\.x" "\\.\\d+")
               ;; *
               (str/replace "*" ".*")
               (->> (str "^"))
               (re-pattern))]
    (some? (re-seq re target-version))))
