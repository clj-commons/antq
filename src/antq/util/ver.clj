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

(defn- digit?
  [c]
  (Character/isDigit (char c)))

(defn- digits?
  [s n]
  (and (= n (count s))
       (every? digit? s)))

(defn timestamped-snapshot?
  "Maven's timestamped snapshot: `<version>-yyyyMMdd.HHmmss-<build number>`
  e.g. \"2.5-20240101.120000-1\" => true"
  [s]
  (let [[build timestamp] (reverse (str/split s #"-"))
        [date time] (some-> timestamp (str/split #"\."))]
    (boolean (and build
                  (seq build)
                  (every? digit? build)
                  date
                  (digits? date 8)
                  time
                  (digits? time 6)))))

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
