(ns antq.util.file-test
  (:require
   [antq.test-helper :as h]
   [antq.test-report]
   [antq.util.file :as sut]
   [antq.util.os :as os]
   [babashka.fs :as fs]
   [clojure.test :as t]
   [matcher-combinators.test]))

(t/deftest tilde->home-test
  (with-redefs [sut/home (constantly (fs/path "/home/foo"))]
    (doseq [[expected input]
            [["~unconverted"                 "~unconverted"]
             ["./~/unconverted"              "./~/unconverted"]
             ["/home/foo"                    "~"]
             ["/home/foo/"                   "~/"]
             ["/home/foo/."                  "~/."]
             ["/home/foo/bippity/boo"        "~/bippity/boo"]
             ["/home/foo/no/../norm/./alize" "~/no/../norm/./alize"]
             [""                             ""]]]
      ;; apply h/os-path to both sides because we can generate \mixed\paths/on/windows
      (t/testing (str "path: " input)
        (t/is (= (h/os-path expected) (h/os-path (sut/tilde->home input))))))))

(t/deftest home->tilde-test
  (with-redefs [sut/home (constantly (fs/path "/home/foo"))]
    (t/is (= (h/os-path "~") (str (sut/home->tilde "/home/foo"))))
    (t/is (= (h/os-path "~") (str (sut/home->tilde "/home/foo/"))))
    (t/is (= (h/os-path "~/bippity/boo") (str (sut/home->tilde "/home/foo/bippity/boo"))))
    (t/is (= (h/os-path "~/no/../norm/./alize") (str (sut/home->tilde "/home/foo/no/../norm/./alize"))))
    (t/is (= (h/os-path "~/.") (str (sut/home->tilde "/home/foo/."))))
    (t/is (= (h/os-path "") (str (sut/home->tilde ""))))))

(t/deftest normalize-path-test
  (let [td (fn [& segs] (str (apply fs/file "target/test/util.file/normalize-path" segs)))]
    (fs/delete-tree (td))
    (fs/create-dirs (td "foo" "bar" "baz"))
    (fs/create-dirs (td "bip" "bop"))
    (fs/create-dirs (td "real" "sub"))
    (fs/spit (td "dupe-filename") ":dupe-under-root")
    (fs/spit (td "real/dupe-filename") ":dupe-under-link-target")
    (fs/spit (td "real/unique-filename") ":unique-under-link-target")
    (fs/create-sym-link (td "link") "real/sub")

    (doseq [[expected input]
            [[(td "foo/bar")  (td "foo/bar")]
             [(td "foo/bar")  (td "foo/./bar")]
             [(td "bip")      (td "foo/../bip")]
             [(td "bip/bop")  (td "foo/../bip/bop")]
             [(td "bip/bop")  (td "foo/../bip/./bop")]
             [".."            ".."]
             ["."             "."]
             ["."             ""] ; treat "" as "."
             ;; when file is not found we return input
             [(td "nope")   (td "nope")]
             ;; and also do minimal safe normalize of leading ./ only
             [(td "nope")   (fs/file  "." (td "nope"))]]]
      (t/testing (str "path: " input)
        (t/is (= (h/os-path expected) (str (sut/normalize-path input))))))


    (t/testing "links"
      ;; links normalize differently on Linux vs Windows

      ;; sanity test for link tests
      (if (os/windows?)
        (t/is (= ":dupe-under-root" (slurp (td "link/../dupe-filename"))) "sanity: slurp with link")
        (t/is (= ":dupe-under-link-target" (slurp (td "link/../dupe-filename"))) "sanity: slurp with link"))
      (t/is (= ":dupe-under-root" (slurp (str (fs/normalize (td "link/../dupe-filename"))))) "sanity: slurp with normalized link")
      (t/is (fs/sym-link? (td "link")) "link created and recognized as such")

      (t/testing "dupe-filename"
        (if (os/windows?)
          ;; on Windows path path .. seems to apply first then file is resolved
          ;; this agrees with JVM normalize behaviour, so path can be normalized
          (t/is (= (h/os-path (td "dupe-filename")) (str (sut/normalize-path (td "link/../dupe-filename")))))
          ;; but on Linux, file seems be resolved first then .. is applied, but normalize does not mimic
          ;; this behaviour so we have a disagreement between OS and JVM so we don't normalize
          (do
            (t/is (= (h/os-path (td "link/../dupe-filename"))
                     (str (sut/normalize-path (td "link/../dupe-filename")))))
            ;; and for paths we do not normalize we still support stripping leading ./
            (t/is (= (h/os-path (td "link/../dupe-filename"))
                     (str (sut/normalize-path (fs/file "." (td "link/../dupe-filename")))))))))

      (t/testing "unique-filename"
        ;; Given the above, OSes behave the same but take different paths internally.
        ;; On Windows the file won't be seen as existing,
        ;; and on Linux/jvm it will exist but will not be seen as the same file after normalize
        ;; and on Linux/bb the same file test actually throws with a no such file exception
        (t/is (= (h/os-path (td "link/../unique-filename"))
                 (str (sut/normalize-path (td "link/../unique-filename")))))
        (t/is (= (h/os-path (td "link/../unique-filename"))
                 (str (sut/normalize-path (fs/file "." (td "link/../unique-filename"))))))))))

(t/deftest detect-project-test
  (t/are [expected file-path] (= expected (sut/detect-project file-path))
    :clojure "/path/to/deps.edn"
    :clojure "/path/to/bb.edn"
    :boot "/path/to/build.boot"
    :gradle "/path/to/build.gradle"
    :leiningen "/path/to/project.clj"
    :shadow-cljs "/path/to/shadow-cljs.edn"
    ::sut/unknown "/path/to/invalid"))

(t/deftest distinct-file-test
  (let [td (fn [& segs] (str (apply fs/file "target/test/util.file/distinct-file-test" segs)))]
    (fs/delete-tree (td))
    (fs/create-dirs (td "foo" "bar" "baz"))
    (doseq [f ["a" "b" "c"]]
      (spit (td f) "foo"))
    (fs/create-dir (td "sub"))
    (doseq [f ["x" "y" "z"]]
      (spit (td "sub" f) "foo"))
    (fs/create-sym-link (td "sub-link") "sub")
    (fs/create-sym-link (td "sub" "z-link") (fs/path "z"))
    (fs/create-sym-link (td "z-link2") (fs/path "sub" "z"))

    (t/is (match? [] (sut/distinct-files []))
          "no files")

    (t/is (match? [(td "a")] (sut/distinct-files [(td "a")]))
          "one file")

    (t/is (match? [(td "a") (td "b") (td "c")]
                  (sut/distinct-files [(td "a") (td "b") (td "c")]))
          "plain files")

    (t/is (match? [(td "a") (td "b") (td "c")]
                  (sut/distinct-files [(td "a") (td "b") (td "c")
                                       (fs/absolutize (td "a"))
                                       (fs/absolutize (td "b"))
                                       (fs/absolutize (td "c"))]))
          "plain files win, absolutes are dupes")
    (t/is (match? [(td "sub-link" "x") (td "sub" "y") (td "sub-link" "z-link")]
                  (sut/distinct-files [(td "sub-link" "x") (td "sub" "x") (td "sub" "y") (td "sub-link" "z-link")
                                       (td "sub" "x") (td "sub-link" "y") (td "sub" "z")
                                       (fs/absolutize (td "sub" "x"))
                                       (fs/absolutize (td "sub" "y"))
                                       (fs/absolutize (td "sub" "z"))
                                       (td "sub-link" "x")
                                       (td "z-link2")]))
          "first wins")))
