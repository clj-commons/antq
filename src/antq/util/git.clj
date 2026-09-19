(ns ^:no-doc antq.util.git
  (:require
   [antq.constant :as const]
   [antq.log :as log]
   [antq.util.async :as u.async]
   [clojure.java.shell :as sh]
   [clojure.string :as str]))

(defn- extract-tags
  [ls-remote-resp]
  (some->> (:out ls-remote-resp)
           (str/split-lines)
           (keep #(second (str/split % #"\t" 2)))
           (filter #(= 0 (.indexOf ^String % "refs/tags")))
           (map #(str/replace % #"^refs/tags/" ""))
           ;; Remove annotated tags
           (remove #(str/ends-with? % "^{}"))))

(defn- same-tag?
  [ref-name s]
  (or (= ref-name s)
      ;; Annotated tag
      (= ref-name (str s "^{}"))))

(defn- extract-sha-by-ref-name
  [ls-remote-resp target-ref-name]
  (some->> (:out ls-remote-resp)
           (str/split-lines)
           (map #(str/split % #"\t" 2))
           (filter (fn [[_ ref-name]]
                     (same-tag? ref-name target-ref-name)))
           (seq)
           ;; NOTE: The annotated tag have priority
           (sort-by second)
           (last)
           (first)))

(defn- ls-remote*
  [url]
  (loop [i 0]
    (when (< i const/retry-limit)
      (let [{:keys [exit err] :as res} (sh/sh "git" "ls-remote" url)]
        (cond
          (= 0 exit)
          res

          (and (< 0 exit) (not (str/includes? err "Operation timed out")))
          (do (log/warning (str "git ls-remote failed on: " url))
              nil)

          :else
          (do (log/warning "git ls-remote timed out, retrying")
              (recur (inc i))))))))

(def ^:private ls-remote*-with-timeout
  (u.async/fn-with-timeout
   ls-remote*
   const/ls-remote-timeout-msec))

(def ^:private ls-remote
  (memoize ls-remote*-with-timeout))

(defn- tags-by-ls-remote*
  [url]
  (-> (ls-remote url)
      (extract-tags)))

(def tags-by-ls-remote
  (memoize tags-by-ls-remote*))

(defn- head-sha-by-ls-remote*
  [url]
  (-> (ls-remote url)
      (extract-sha-by-ref-name "HEAD")))

(defn- tag-sha-by-ls-remote*
  [url tag-name]
  (-> (ls-remote url)
      (extract-sha-by-ref-name (str "refs/tags/" tag-name))))

(def head-sha-by-ls-remote
  (memoize head-sha-by-ls-remote*))

(def tag-sha-by-ls-remote
  (memoize tag-sha-by-ls-remote*))

(defn find-tag
  [url s]
  (when (and (seq url) (seq s))
    (let [tags (tags-by-ls-remote url)]
      (or
       ;; If there is an exact match, it is preferred
       (some #(and (= % s) %) tags)
       (some #(and (str/includes? % s) %) tags)))))
