(ns ^:no-doc antq.util.file
  (:require
   [antq.constant.project-file :as const.project-file]
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn home
  "Abstracted for redef testing."
  []
  (fs/file (fs/home)))

(defn- strip-leading-dot-slash
  [file-path]
  (let [components (fs/components file-path)]
    (if (and (= "." (-> components first str))
             (seq (rest components)))
      (apply fs/file (rest components))
      file-path)))

(defn normalize-path
  "Best effort normalize.

  Return `file-path` with redundant segments removed.

  If cannot confirm that normalized variant is same physical file as `file-path`,
  falls back to removing leading `./` only. Examples when this happens:
  - `file-path` does not exist
  - `file-path` contains a symbolic link that would make it different from its normalized form."
  [file-path]
  (if-not (fs/exists? file-path)
    ;; fallback to a minimal safe normalize
    (strip-leading-dot-slash file-path)
    (let [n (fs/normalize file-path)]
      (if (try ; some implementations will throw when file does not exist
            (fs/same-file? n file-path)
            (catch java.io.IOException _ false))
        ;; The JVM treats "" as equivalent to cwd, but this is unintuitive to users, so we use .
        (if (= "" (str n))
          "."
          n)
        ;; could a symlink in path that makes normalized file diff from orig
        ;; fallback to a minimal safe normalize
        (strip-leading-dot-slash file-path)))))

(defn home->tilde
  [file-path]
  (let [components (fs/components file-path)]
    (if (fs/starts-with? file-path (home))
      (apply fs/file "~" (drop (count (fs/components (home))) components))
      file-path)))

(def ^:private valid-file-sep-chars
  "On windows JVM supports host specific backslash or forward slash"
  (into #{} [(first fs/file-separator) \/]))

(defn tilde->home
  "Returns `file-path` with leading `~/` replaced with user home dir.
  Purposefully does not convert `~username`."
  [file-path]
  ;; - opted to not use fs/expand-home, it converts does ~username to /home/username which we don't want
  ;; - we work at string level to avoid throws on invalid chars in file-path
  (let [f (str file-path)]
    (if (= "~" f)
      (str (home))
      (let [[tilde sep] f]
        (if (and (= \~ tilde)
                 (valid-file-sep-chars sep))
          (str (home) fs/file-separator (subs f 2))
          f)))))

(defn text-friendly-path
  "Return `file-path` for use in text table report or user prompts."
  [file-path]
  (-> file-path
      normalize-path
      home->tilde
      str))

(defn data-friendly-path
  "Return `file-path` for use in data, like an edn or json report."
  [file-path]
  (-> file-path
      normalize-path
      str))

(defn detect-project
  [file-path]
  (condp #(str/ends-with? %2 %1) file-path
    const.project-file/babashka :clojure
    const.project-file/boot :boot
    const.project-file/clojure-cli :clojure
    const.project-file/gradle :gradle
    const.project-file/leiningen :leiningen
    const.project-file/maven :pom
    const.project-file/shadow-cljs :shadow-cljs
    ::unknown))

(defn distinct-files
  "Returns distinct files (can be dirs), weeding out duplicates while preserving entries.
  First distinct occurrence wins. All `files` must exist."
  [files]
  (->> (reduce (fn [{:keys [unique-files] :as acc} orig]
                 (let [real (fs/real-path orig)]
                   (if-not (unique-files real)
                     (-> acc
                         (update :unique-files conj real)
                         (update :orig-distinct conj orig))
                     acc)))
               {:unique-files #{} :orig-distinct []}
               files)
       :orig-distinct))

(defn first-eol
  "Returns the first eol in `f`"
  [f]
  (with-open [in (io/input-stream f)]
    (loop []
      (let [b (.read in)]
        (case b
          -1 nil
          10 "\n"
          13 (if (= 10 (.read in)) "\r\n" "\r")
          (recur))))))
