(ns antq.log-test
  (:require
   [antq.log :as sut]
   [antq.test-helper :as h]
   [antq.test-report]
   [clojure.string :as str]
   [clojure.test :as t]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test]))

(t/deftest debug-test
  (t/is (match? {:err m/absent
                 :out m/absent}
                (h/with-output-captured (sut/debug "debug message"))))
  (t/testing "verbose"
    (t/is (match? {:err #".*DEBUG.* debug message"
                   :out m/absent}
                  (binding [sut/*verbose* true]
                    (h/with-output-captured (sut/debug "debug message")))))))

(t/deftest info-test
  (t/is (match? {:err #".*INFO.* info message"
                 :out m/absent}
                (h/with-output-captured (sut/info "info message")))))

(t/deftest warn-test
  (t/is (match? {:err #".*WARN.* warn message"
                 :out m/absent}
                (h/with-output-captured (sut/warn "warn message")))))

(t/deftest error-test
  (t/is (match? {:err #".*ERROR.* error message"
                 :out m/absent}
                (h/with-output-captured (sut/error "error message")))))

(t/deftest multi-line-test
  (t/is (match? {:err (m/via str/split-lines [#"(?s).*ERROR.* one two"
                                              "  buckle my"
                                              "  shoe"])
                 :out m/absent}
                (h/with-output-captured (sut/error (str "one two\n"
                                                        "buckle my\n"
                                                        "shoe"))))))
