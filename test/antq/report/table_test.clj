(ns antq.report.table-test
  (:require
   [antq.report :as report]
   [antq.report.table]
   [antq.test-helper :as h]
   [antq.test-report]
   [clojure.string :as str]
   [clojure.test :as t]))

(defn- reporter
  ([deps]
   (reporter deps {}))
  ([deps options]
   (report/reporter deps (assoc options :reporter "table"))))

(t/deftest reporter-test
  (let [dummy-deps [(h/test-dep {:file "a" :name "foo" :version "1" :latest-version "2"})
                    (h/test-dep {:file "b" :name "bar" :version "1" :latest-version nil})
                    (h/test-dep {:file "c" :name "baz" :version "2" :latest-version "3" :changes-url "here"})
                    (h/test-dep {:file "c" :name "old" :version "1" :latest-version nil :latest-name "new"})]
        outputs (str/split-lines (with-out-str (reporter dummy-deps)))]
    (t/is (some #(and (str/includes? % "foo") (str/includes? % "1") (str/includes? % "2")) outputs))
    (t/is (some #(and (str/includes? % "bar") (str/includes? % "Failed to fetch")) outputs))
    (t/is (some #(and (str/includes? % "baz") (str/includes? % "2") (str/includes? % "3")) outputs))
    (t/is (some #(and (str/includes? % "old") (str/includes? % "new")) outputs))
    (t/is (some #(str/includes? % "here") outputs))

    ;; no outdated deps
    (t/is (str/includes? (with-out-str (reporter []))
                         "All dependencies are up-to-date"))))

(defn- days-ago
  [n]
  (str (.minus (java.time.Instant/now) (java.time.Duration/ofDays n))))

(defn- cells
  [row]
  (mapv str/trim (rest (str/split row #"\|"))))

(defn- table
  [deps & [options]]
  (let [[header _ & rows] (str/split-lines (with-out-str (reporter deps options)))]
    (map cells (cons header rows))))

(t/deftest reporter-headings-test
  (let [dep (h/test-dep {:file "a" :name "foo" :version "1" :latest-version "2"
                         :changes-url "here"})]
    (t/testing "no release dates, no age or cooldown columns"
      (t/is (= [["file" "name" "current" "latest"]
                ["a" "foo" "1" "2"]]
               (take 2 (table [dep])))))
    (t/testing "ages next to their versions"
      (t/is (= [["file" "name" "current" "age" "latest" "age"]
                ["a" "foo" "1" "40d" "2" "2d"]]
               (take 2 (table [(assoc dep :released-at (days-ago 40) :latest-released-at (days-ago 2))])))))
    (t/testing "changes in table"
      (t/is (= [["file" "name" "current" "latest" "changes-url"]
                ["a" "foo" "1" "2" "here"]]
               (take 2 (table [dep] {:changes-in-table true})))))))

(t/deftest reporter-cooldown-test
  (let [deps [(h/test-dep {:file "a" :name "foo" :version "1" :latest-version "3"
                           :released-at (days-ago 40) :latest-released-at (days-ago 2)
                           :cooldown "7d" :cooldown-version "2" :cooldown-released-at (days-ago 9)})
              (h/test-dep {:file "a" :name "bar" :version "1" :latest-version "2"
                           :released-at (days-ago 9) :latest-released-at nil
                           :cooldown "7d" :cooldown-version nil :cooldown-released-at nil})
              (h/test-dep {:file "a" :name "baz" :version "1" :latest-version "2"})]]
    (t/is (= [["file" "name" "current" "age" "cooldown" "age" "dur" "latest" "age"]
              ["a" "bar" "1" "9d" "1" "9d" "7d" "2" ""]
              ["" "baz" "1" "" "" "" "" "2" ""]
              ["" "foo" "1" "40d" "2" "9d" "7d" "3" "2d"]]
             (take 4 (table deps))))))
