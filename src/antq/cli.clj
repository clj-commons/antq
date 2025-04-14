(ns ^:no-doc antq.cli
  (:require
   [babashka.cli :as cli]
   [clojure.string :as str]))

(def ^:private supported-reporter
  ["table"
   "format"
   "json"
   "edn"])

(def ^:private skippable
  ["babashka"
   "boot"
   "circle-ci"
   "clojure-cli"
   "github-action"
   "gradle"
   "leiningen"
   "pom"
   "shadow-cljs"])

(declare styled-long-opt)

(defn- multi-value
  [coll arg-value]
  (into (or coll [])
        (str/split (str arg-value) #":")))

(defn- multi-value-tip
  [option sample-values]
  {:clojure-tool (format "for multiple, use a vector, ex: '[%s]'\n or use colon separators: %s"
                         (str/join " " sample-values) (str/join ":" sample-values))
   :cli (format "for multiple, repeat arg, ex: %s\n or use colon separators: %s=%s"
                (str/join " " (mapv #(str option "=" %) sample-values))
                option (str/join ":" sample-values))})

(def ^:private cli-options
  {:exclude
   {:ref "<artifact-name[@version]>"
    :collect multi-value
    :desc "Skip version checking for specified artifacts or versions"
    :extra-desc (multi-value-tip "--exclude" ["art1" "art2@1.23"])}

   :focus
   {:ref "<artifact-name>"
    :collect multi-value
    :desc "Only version check for specified artifacts"
    :extra-desc (multi-value-tip "--focus" ["art1" "art2"])}

   :skip
   {:ref (format "<%s>" (str/join "|" skippable))
    :collect multi-value
    :validate #(every? (set skippable) %)
    :desc "Skip specified project file types"
    :extra-desc (multi-value-tip "--format" ["pom" "gradle"])}

   :error-format
   {:ref "<error format>"
    :coerce :string
    :desc "Customize output for outdated dependencies"}

   :reporter
   {:ref (format "<%s>" (str/join "|" supported-reporter))
    :coerce :string
    :default "table"
    :validate #((set supported-reporter) %)
    :desc "Report output format"}

   :directory
   {:alias :d
    :ref "<directory>"
    :collect (fn multi-value
               [coll arg-value]
               ;; "." is not optional/overideable
               (into (or coll ["."])
                     (str/split arg-value #":")))
    :default ["."]
    :default-desc "./"
    :desc "Add search paths for projects (in addition to ./)"
    :extra-desc (multi-value-tip "--directory" ["./dira" "./dirb"])}

   :upgrade
   {:coerce boolean
    :desc "Upgrade outdated versions interactively"}

   :verbose
   {:coerce boolean
    :desc "Verbose logging"}

   :force
   {:coerce boolean
    :desc "Use with upgrade for non-interactive upgrade"}

   :download
   {:coerce boolean
    :desc "Download updated dependencies"}

   :ignore-locals
   {:coerce boolean
    :desc "Ignore versions installed in your local maven repository"}

   :check-clojure-tools
   {:coerce boolean
    :desc "Detect outdated clojure tools in ~/.clojure/tools"}

   :no-diff
   {:coerce :boolean
    :deprecated-fn (fn [m] (format "Please use %s instead." (styled-long-opt :no-changes m)))}

   :no-changes
   {:coerce boolean
    :desc "Skip checking changes between deps versions"}

   :changes-in-table
   {:coerce boolean
    :desc "Show changes URLs in table when using table reporter"}

   :transitive
   {:coerce boolean
    :desc "Scan outdated transitive deps"}

   :usage-help-style
   {:coerce :keyword
    :default :cli
    :desc "Internal opt to control style of usage help"}

   :help
   {:alias :h
    :coerce :boolean
    :desc "Show usage help"}})

(defn- kw->str
  [kw]
  (subs (str kw) 1))

(defn- styled-long-opt
  [longopt {:keys [usage-help-style]}]
  (if (= :clojure-tool usage-help-style)
    longopt
    (str "--" (kw->str longopt))))

(defn- styled-alias
  [alias {:keys [usage-help-style]}]
  (if (= :clojure-tool usage-help-style)
    alias
    (str "-" (kw->str alias))))

(defn- wrap-words
  [words wrap-at]
  (reduce (fn [acc enum]
            (let [row-ndx (dec (count acc))
                  enums-len (reduce + (map count (last acc)))]
              (if (> enums-len wrap-at)
                (conj acc [enum])
                (update acc row-ndx conj enum))))
          [[]]
          words))

(defn- wrapped-option-ref
  [option ref wrap-at]
  (let [rows (wrap-words (str/split ref #"\|") wrap-at)]
    (str option (->> rows
                     (mapv #(str/join "|" %))
                     (str/join (str "\n" (apply str (repeat (inc (count option)) " ")) "|"))))))

(defn- fmt-option-ref
  [long-opt ref {:keys [usage-help-style] :as opts}]
  (let [option (styled-long-opt long-opt opts)
        option (if (and ref (= :cli usage-help-style))
                 (str option "=")
                 (str option " "))
        wrap-at 20]
    (if (and ref (str/includes? ref "|") (> (+ (count ref) (count option)) wrap-at))
      (wrapped-option-ref option ref wrap-at)
      (str option ref))))

(defn- opts->table
  "Based on bb cli opts->table but uses less screen width."
  [{:keys [spec order opts]}]
  (let [usage-help-style (:usage-help-style opts)]
    (mapv (fn [[long-opt {:keys [alias default default-desc ref desc extra-desc]}]]
            (let [alias (if alias
                          (str (styled-alias alias opts) ",")
                          "")
                  option (fmt-option-ref long-opt ref opts)
                  desc (->> [(if-let [default (or default-desc
                                                  (when (some? default) (str default)))]
                               (format "%s\n default: %s" desc default)
                               desc)
                             (when-let [extra-desc (get extra-desc usage-help-style)]
                               (str " " extra-desc))]
                            (keep identity)
                            (str/join "\n"))]

              [alias option desc]))
          (let [order (or order (keys spec))]
            (map (fn [k] [k (spec k)]) order)))))

(defn- format-opts
  "customized bb cli format-opts"
  [{:as cfg}]
  (cli/format-table {:rows (opts->table cfg) :indent 1}))

(defn- deprecation-warnings
  [opts]
  (into [] (keep
            (fn [deprecated-opt]
              (when (deprecated-opt opts)
                {:type :antq/cli
                 :cause :deprecation
                 :msg (format "%s is deprecated and will be deleted in a future release. %s"
                              (styled-long-opt deprecated-opt opts)
                              ((-> cli-options deprecated-opt :deprecated-fn) opts))}))
            [:no-diff])))

(defn- opts->args
  [m]
  (->> m
       (reduce (fn [acc [k v]]
                 (if (vector? v)
                   (apply conj acc (interleave (repeat k) v))
                   (conj acc k v)))
               [])
       (mapv #(if-not (string? %) (pr-str %) %))))

(defn usage-help
  "Return usage help as a string

  opts:
  - `:usage-help-style`
    - `:cli` for -M style help
    - `:clojure-tool` for -T and -X style help "
  [{:keys [opts]}]
  (str "antq ARG USAGE:\n"
       " [options..]\n"
       "\n"
       (format-opts {:spec cli-options :opts opts
                     ;; match order from README, exclude deprecated and undocumented options
                     :order [:upgrade :force :exclude :directory :focus
                             :skip :error-format :reporter :download :ignore-locals
                             :check-clojure-tools :no-changes :changes-in-table :transitive
                             :help]})))

(defn parse-args
  "Parses command line `args` and returns a map of:
  - `:warnings` - non-fatal issues, caller should dispaly
  - `:errors` - fatal issues, caller should display
  - `:help` - usage help as text, caller should display
  - `:opts` - parsed opts

  It is assumed caller will exit process on existence of:
  - `:errors` - non-zero exit
  - `:help` only - zero exit"
  [args]
  (let [errors (atom [])
        orig-args args
        {:keys [opts]} (cli/parse-args args {:spec (select-keys cli-options [:help :usage-help-style])})]
    (if (:help opts)
      {:help (usage-help {:opts opts})}
      (let [{:keys [args opts]} (cli/parse-args orig-args
                                                {:spec cli-options
                                                 :error-fn (fn [error] (swap! errors conj error))
                                                 :restrict true})
            errors @errors
            warnings (deprecation-warnings opts)
            errors (if (seq args)
                     (conj errors {:type :antq/cli
                                   :cause :invalid-command
                                   :msg (format "Antq supports no cli commands, but found: %s" (str/join ", " args))
                                   :spec cli-options})
                     errors)]
        (cond-> {}
          (seq warnings)
          (assoc :warnings warnings)

          (seq errors)
          (assoc :errors errors :help (usage-help {:opts opts}))

          :else
          (assoc :opts opts))))))

(defn validate-tool-opts
  "Validate -T and -X style delivered `opts`, see `parse-args` for returns."
  [opts]
  (->> opts
       opts->args
       (into [":usage-help-style" ":clojure-tool"])
       parse-args))
