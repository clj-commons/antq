(ns helper.git
  (:require
   [babashka.tasks :as t]
   [clojure.string :as str]
   [version-clj.core :as v]))

(defn not-fork?
  [{:keys! [github-coords]}]
  (let [origin-url (->> (t/shell {:out :string} "git remote get-url origin")
                        :out
                        str/trim)]
    (some #{origin-url} [(format "https://github.com/%s.git" github-coords)
                         (format "git@github.com:%s.git" github-coords)])))

(defn default-branch?
  [{:keys! [default-branch]}]
  (let [current-branch (->> (t/shell {:out :string} "git rev-parse --abbrev-ref HEAD")
                            :out
                            str/trim)]
    (= default-branch current-branch)))

(defn uncommitted-code?
  []
  (-> (t/shell {:out :string}
               "git status --porcelain")
      :out
      str/trim
      seq))

(defn local-branch?
  []
  (let [{:keys! [exit]} (t/shell {:continue true :out :string :err :out}
                                 "git rev-parse --symbolic-full-name @{u}")]
    (not (zero? exit))))

(defn unpushed-commits?
  []
  (let [{:keys! [exit out]} (t/shell {:continue true :out :string}
                                     "git cherry -v")]
    (and (zero? exit) (-> out str/trim seq))))

(defn commit-matches-default-head?
  [{:keys! [default-branch github-coords]}]
  (let [repo-head-sha (-> (t/shell {:out :string} (format "git ls-remote https://github.com/%s.git %s"
                                                          github-coords default-branch))
                          :out
                          (str/split #"\s+")
                          first)
        local-head-sha (-> (t/shell {:out :string} "git rev-parse HEAD")
                           :out
                           str/trim)]
    (= repo-head-sha local-head-sha)))

(defn- raw-tags
  [{:keys! [github-coords]}]
  (->>  (t/shell {:out :string}
                 ;; specifying the url ensures we don't get tags for some dev fork
                 "git ls-remote --tags --refs" (format "https://github.com/%s.git" github-coords))
        :out
        str/split-lines))

(defn- parse-raw-tag
  [raw-tag-line]
  (when-let [tag (some-> (re-find #"refs/tags/(\d+\..*)" raw-tag-line)
                         last)]
    ;; tag and version are same for antq
    {:tag tag :version tag}))

(defn- most-recent-tag
  [parsed-tags]
  (->>  parsed-tags
        (sort-by :version v/version-compare)
        reverse
        first
        :tag))

(defn last-version-tag
  [config]
  (->>  (raw-tags config)
        (keep #(parse-raw-tag %))
        (most-recent-tag)))

(defn head-sha
  []
  (-> (t/shell {:out :string} "git rev-parse HEAD") :out str/trim))

(comment
  (last-version-tag {:github-coords "clj-commons/antq"})
  ;; => "2.11.1276"

  :eoc)
