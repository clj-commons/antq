(ns antq.log-test
  (:require
   [antq.log :as sut]
   [clojure.test :as t]))

(t/deftest info-test
  (t/is (= "INFO\n"
           (with-out-str (sut/info "INFO")))))

(t/deftest error-test
  (let [sw (java.io.StringWriter.)
        err-str (binding [*err* sw]
                  (sut/error "ERROR")
                  (str sw))]
    (t/is (= "ERROR\n" err-str))))

(t/deftest warning-test
  (t/testing "verbose false"
    (let [sw (java.io.StringWriter.)
          warn-str (binding [sut/*verbose* false
                             *err* sw]
                     (sut/warning "WARNING")
                     (str sw))]
      (t/is (= "" warn-str))))

  (t/testing "verbose true"
    (let [sw (java.io.StringWriter.)
          warn-str (binding [sut/*verbose* true
                             *err* sw]
                     (sut/warning "WARNING")
                     (str sw))]
      (t/is (= "WARNING\n" warn-str)))))
