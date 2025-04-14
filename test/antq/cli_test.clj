(ns antq.cli-test
  (:require
   [antq.cli :as sut]
   [clojure.test :as t]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test]))

(t/deftest cli-options-test
  (t/testing "default options"
    (t/is (= {:opts {:directory ["."]
                     :reporter "table"
                     :usage-help-style :cli}}
             (sut/parse-args []))))

  (t/testing "--exclude"
    (t/is (match? {:errors m/absent
                   :warnings m/absent
                   :help m/absent
                   :opts {:exclude ["ex/ex1" "ex/ex2" "ex/ex3"
                                    "ex/ex4@1.0.0" "ex/ex5@2.0.0" "ex/ex6@3.0.0"]}}
                  (sut/parse-args ["--exclude=ex/ex1"
                                   "--exclude=ex/ex2:ex/ex3"
                                   "--exclude=ex/ex4@1.0.0"
                                   "--exclude=ex/ex5@2.0.0:ex/ex6@3.0.0"]))))

  (t/testing "--focus"
    (t/is (match? {:errors m/absent
                   :warnings m/absent
                   :help m/absent
                   :opts {:focus ["fo/cus1" "fo/cus2" "fo/cus3"]}}
                  (sut/parse-args ["--focus=fo/cus1"
                                   "--focus=fo/cus2:fo/cus3"]))))

  (t/testing "--directory"
    (t/is (match? {:errors m/absent
                   :warnings m/absent
                   :help m/absent
                   :opts {:directory ["." "dir1" "dir2" "dir3" "dir4"]}}
                  (sut/parse-args ["-d" "dir1"
                                   "--directory=dir2"
                                   "--directory" "dir3:dir4"]))))

  (t/testing "--skip"
    (t/is (match? {:errors m/absent
                   :warnings m/absent
                   :help m/absent
                   :opts {:skip ["boot" "clojure-cli"]}}
                  (sut/parse-args ["--skip" "boot"
                                   "--skip=clojure-cli"])))

    (t/testing "validation error"
      (t/is (match? {:help #"(?s).*USAGE.*--upgrade.*repeat arg"
                     :warnings m/absent
                     :errors [{:cause :validate :msg #"Invalid value.*skip.*foo"}]}
                    (sut/parse-args ["--skip=foo"])))))

  (t/testing "--error-format"
    (t/is (match? {:errors m/absent
                   :warnings m/absent
                   :help m/absent
                   :opts {:error-format "foo"}}
                  (sut/parse-args ["--error-format=foo"]))))

  (t/testing "--reporter"
    (t/is (match? {:errors m/absent
                   :warnings m/absent
                   :help m/absent
                   :opts {:reporter "edn"}}
                  (sut/parse-args ["--reporter=edn"])))

    (t/testing "validation error"
      (t/is (match? {:help #"(?s).*USAGE.*--upgrade.*repeat arg"
                     :warnings m/absent
                     :errors [{:cause :validate :msg #"Invalid value.*reporter.*foo"}]}
                    (sut/parse-args ["--reporter=foo"])))))

  (t/testing "--upgrade"
    (t/is (match? {:errors m/absent
                   :warnings m/absent
                   :help m/absent
                   :opts {:upgrade true}}
                  (sut/parse-args ["--upgrade"]))))

  (t/testing "--no-diff"
    (t/is (match? {:errors m/absent
                   :warnings [{:cause :deprecation
                               :msg #"no-diff.*deprecated.*use.*no-changes"}]
                   :help m/absent
                   :opts {:no-diff true}}
                  (sut/parse-args ["--no-diff"]))))

  (t/testing "all opts - all valid"
    (t/is (match? {:errors m/absent
                   :warnings m/absent
                   :help m/absent
                   :opts {:exclude ["art1" "art2@1.2.3" "art7"]
                          :focus ["art3" "art4"]
                          :skip ["pom" "gradle" "leiningen"]
                          :error-format "my error format"
                          :reporter "json"
                          :directory ["." "two" "three"]
                          :upgrade true
                          :verbose true
                          :force true
                          :download true
                          :ignore-locals true
                          :check-clojure-tools true
                          :no-changes true
                          :changes-in-table true
                          :transitive true}}
                  (sut/parse-args ["--exclude" "art1:art2@1.2.3" "--exclude" "art7"
                                   "--focus" "art3" "--focus" "art4"
                                   "--skip" "pom:gradle" "--skip" "leiningen"
                                   "--error-format" "my error format"
                                   "--reporter" "json"
                                   "--directory" "two" "-d" "three"
                                   "--upgrade"
                                   "--verbose"
                                   "--force"
                                   "--download"
                                   "--ignore-locals"
                                   "--check-clojure-tools"
                                   "--no-changes"
                                   "--changes-in-table"
                                   "--transitive"]))))

  (t/testing "errors and warnings"
    (t/is (match? {:help #"(?s).*USAGE.*--upgrade.*repeat arg"
                   :warnings [{:cause :deprecation
                               :msg #"no-diff.*deprecated.*use.*no-changes"}]
                   :errors [{:cause :restrict :msg #"Unknown option.*excude"}
                            {:cause :validate :msg #"Invalid value.*reporter.*foo"}
                            {:cause :validate :msg #"Invalid value.*skip.*nope"}
                            {:cause :invalid-command :msg #"Antq supports no cli commands.*somecmd"}]}
                  (sut/parse-args ["somecmd"
                                   "--reporter" "foo"
                                   "--skip" "nope"
                                   "--no-diff"
                                   "--excude" "spello"]))))

  (t/testing "--help"
    (t/is (match? {:errors m/absent
                   :warnings m/absent
                   :help #"(?s).*USAGE.*--upgrade.*repeat arg"}
                  (sut/parse-args ["--help"])))))

(t/deftest validate-tool-opts
  (t/testing "defaults"
    (t/is (= {:opts {:directory ["."]
                     :reporter "table"
                     :usage-help-style :clojure-tool}}
             (sut/validate-tool-opts {}))))

  (t/testing "multi-value"
    (t/is (match? {:errors m/absent
                   :warnings m/absent
                   :help m/absent
                   :opts {:directory ["." "foo" "bar"]}}
                  (sut/validate-tool-opts {:directory ['foo 'bar]}))))

  (t/testing "validated"
    (t/is (match? {:errors m/absent
                   :warnings m/absent
                   :help m/absent
                   :opts {:reporter "edn"}}
                  (sut/validate-tool-opts {:reporter "edn"}))))

  (t/testing "deprecated"
    (t/is (match? {:errors m/absent
                   :warnings [{:cause :deprecation
                               :msg #"no-diff.*deprecated.*use.*no-changes"}]
                   :help m/absent
                   :opts {:no-diff true}}
                  (sut/validate-tool-opts {:no-diff true}))))

  (t/testing "all opts - all valid"
    (t/is (match? {:errors m/absent
                   :warnings m/absent
                   :help m/absent
                   :opts {:exclude ["art1" "art2@1.2.3"]
                          :focus ["art3" "art4"]
                          :skip ["pom" "gradle" "leiningen"]
                          :error-format "my error format"
                          :reporter "json"
                          :directory ["." "two" "three"]
                          :upgrade true
                          :verbose true
                          :force true
                          :download true
                          :ignore-locals true
                          :check-clojure-tools true
                          :no-changes true
                          :changes-in-table true
                          :transitive true}}
                  (sut/validate-tool-opts {:exclude "art1:art2@1.2.3" ; try colon separated for multi
                                           :focus ["art3" "art4"]     ; try vector for multi
                                           :skip "pom:gradle:leiningen"
                                           :error-format "my error format"
                                           :reporter "json"
                                           :directory ["two" "three"]
                                           :upgrade true
                                           :verbose true
                                           :force true
                                           :download true
                                           :ignore-locals true
                                           :check-clojure-tools "FOO" ; test coercion to boolean
                                           :no-changes true
                                           :changes-in-table true
                                           :transitive true}))))

  (t/testing "errors and warnings"
    (t/is (match? {:help #"(?s).*USAGE.*:upgrade.*use a vector"
                   :warnings [{:cause :deprecation
                               :msg #"no-diff.*deprecated.*use.*no-changes"}]
                   :errors [{:cause :restrict :msg #"Unknown option.*excude"}
                            {:cause :validate :msg #"Invalid value.*reporter.*foo"}
                            {:cause :validate :msg #"Invalid value.*skip.*nope"}]}
                  (sut/validate-tool-opts {:reporter "foo"
                                           :skip ["nope"]
                                           :no-diff true
                                           :excude "spello"}))))

  (t/testing "help"
    (t/is (match? {:errors m/absent
                   :warnings m/absent
                   :help #"(?s).*USAGE.*:upgrade.*use a vector"}
                  (sut/validate-tool-opts {:help true})))))
