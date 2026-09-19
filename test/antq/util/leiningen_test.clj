(ns antq.util.leiningen-test
  (:require
   [antq.util.env :as u.env]
   [antq.util.leiningen :as sut]
   [clojure.test :as t]))

(def ^:private dummy-credentials
  {#"https://one.example.com/repository/.*"
   {:username "one-username"
    :password "one-password"}
   #"https://two.example.com/repository/.*"
   {:username "two-username"
    :password "two-password"}
   #"three.example.com/repository/"
   {:username "three-username"
    :password "three-password"}})

(t/deftest env-test
  (with-redefs [u.env/getenv identity]
    (t/is (= "LEIN_PASSWORD" (sut/env :env)))
    (t/is (= "FOO" (sut/env :env/foo)))
    (t/is (= "FOO_BAR" (sut/env :env/foo_bar)))
    (t/is (= nil (sut/env :invalid/foo_bar)))
    (t/is (= nil (sut/env "string")))))

(t/deftest get-credential-test
  (with-redefs [sut/credentials-fn (constantly dummy-credentials)]
    (let [url1 "https://one.example.com/repository/releases"
          url2 "https://two.example.com/repository/releases"
          url3 "https://three.example.com/repository/releases"
          url4 "https://four.example.com/repository/releases"]
      (t/is (= "one-username" (:username (sut/get-credential url1))))
      (t/is (= "one-password" (:password (sut/get-credential url1))))
      (t/is (= "two-username" (:username (sut/get-credential url2))))
      (t/is (= "two-password" (:password (sut/get-credential url2))))
      (t/is (= "three-username" (:username (sut/get-credential url3))))
      (t/is (= "three-password" (:password (sut/get-credential url3))))
      (t/is (= nil (:username (sut/get-credential url4))))
      (t/is (= nil (:password (sut/get-credential url4)))))))
