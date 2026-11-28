(ns antq.log-test
  (:require
   [antq.log :as sut]
   [antq.test-helper :as h]
   [antq.test-report]
   [antq.util.ansi :as ansi]
   [clojure.string :as str]
   [clojure.test :as t]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test]))

(t/deftest debug-test
  (t/is (match? {:err m/absent
                 :out m/absent}
                (h/with-output-captured (sut/debug "debug message"))))
  (t/testing "verbose"
    (t/is (match? {:err #"\u001b.*[DEBUG].* debug message"
                   :out m/absent}
                  (binding [sut/*verbose* true]
                    (h/with-env {"NO_COLOR" nil}
                                (h/with-output-captured (sut/debug "debug message"))))))))

(t/deftest info-test
  (t/is (match? {:err #"\u001b.*[INFO].* info message"
                 :out m/absent}
                (h/with-output-captured
                 (h/with-env {"NO_COLOR" nil}
                             (sut/info "info message"))))))

(t/deftest warn-test
  (t/is (match? {:err #"\u001b.*[WARN].* warn message"
                 :out m/absent}
                (h/with-output-captured
                 (h/with-env {"NO_COLOR" nil}
                             (sut/warn "warn message"))))))

(t/deftest error-test
  (t/is (match? {:err #"\u001b.*[ERROR].* error message"
                 :out m/absent}
                (h/with-output-captured
                 (h/with-env {"NO_COLOR" nil}
                             (sut/error "error message"))))))

(t/deftest multi-line-test
  (t/is (match? {:err (m/via str/split-lines [#"(?s)\u001b.*ERROR.* one two"
                                              "  buckle my"
                                              "  shoe"])
                 :out m/absent}
                (h/with-output-captured
                 (h/with-env {"NO_COLOR" nil}
                             (sut/error (str "one two\n"
                                             "buckle my\n"
                                             "shoe")))))))

(t/deftest no-color-test
  ;; sanity tests
  (t/is (match? {:err (m/via str/trim "[ERROR] error message")
                 :out m/absent}
                (binding [ansi/*no-color* true]
                  (h/with-output-captured (sut/error "error message")))))
  (t/is (match? {:err (m/via str/trim "[WARN] warn message")
                 :out m/absent}
                (h/with-env {"NO_COLOR" "DEFINED"}
                            (h/with-output-captured (sut/warn "warn message"))))))
