(ns antq.util.xml-test
  (:require
   [antq.test-report]
   [antq.util.xml :as sut]
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.test :as t]))

(def ^:private test-xml
  (-> "util/xml/dummy.xml"
      io/resource
      slurp
      xml/parse-str
      xml-seq))

(def ^:private test-entries
  (sut/get-tags :entry test-xml))

(t/deftest get-value-test
  (t/is (= "Foo 1.0.0" (->> test-entries first :content (sut/get-value :title))))
  (t/is (nil? (->> test-entries first :content (sut/get-value :unknown)))))

(t/deftest get-values-test
  (t/is (= ["tag:github.com,2008:Repository/928777/1.0.0" "Foo 1.0.0"]
           (->> test-entries first :content (sut/get-values [:id :title]))))
  (t/is (= [nil "Foo 1.0.0"]
           (->> test-entries first :content (sut/get-values [:unknown :title]))))
  (t/is (= [nil nil]
           (->> test-entries first :content (sut/get-values [:unknown :unknown])))))

(t/deftest get-attribute-test
  (t/is (= "https://github.com/foo/bar/releases/tag/1.0.0"
           (->> test-entries first :content (sut/get-attribute :link :href))))
  (t/is (nil? (->> test-entries first :content (sut/get-attribute :link :unknown))))
  (t/is (nil? (->> test-entries first :content (sut/get-attribute :unknown :href)))))
