(ns antq.util.ansi-test
  (:require
   [antq.test-helper :as h]
   [antq.util.ansi :as sut]
   [clojure.test :as t]
   [matcher-combinators.test]))

(t/deftest ansi-enabled-test
  (h/with-env {"NO_COLOR" nil} ; just in case a dev has NO_COLOR in their env
              (t/is (= [(char 27) \[ \3 \6 \; \2 \; \4 \1 \m] (seq (sut/ctrl :fg-cyan :dim :bg-red))))))

(t/deftest ansi-disabled-test
  (binding [sut/*no-color* true]
    (t/is (= "" (sut/ctrl :fg-cyan :dim :bg-red))))
  (h/with-env {"NO_COLOR" "TRUE"}
              (t/is (= "" (sut/ctrl :fg-cyan :dim :bg-red)))))
