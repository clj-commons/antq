(ns antq.util.maven-test
  (:require
   [antq.test-helper :as h]
   [antq.util.env :as u.env]
   [antq.util.leiningen :as u.lein]
   [antq.util.maven :as sut]
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.test :as t])
  (:import
   java.util.UUID))

(def ^:private dummy-repos
  {"serv3" {:url "https://three.example.com"
            :username "three-user"
            :password "three-pass"}
   "serv4" {:url "https://four.example.com"
            :username :env
            :password :env/four}
   "serv5" {:url "https://five.example.com"
            :creds :gpg}
   ;; no username and password
   "dummy" {:url "https://dummy.example.com"}})

(def ^:private dummy-env
  {"LEIN_PASSWORD" "lein-pass"
   "FOUR" "env-four"})

(def ^:private test-pom-file
  (io/file (io/resource "util/maven/pom.xml")))

(t/deftest normalize-repo-url-test
  (t/are [expected in] (= expected (sut/normalize-repo-url in))
    "" ""
    "foo" "foo"
    "s3://foo/bar" "s3p://foo/bar"))

(t/deftest normalize-repos-test
  (t/is (= sut/default-repos
           (sut/normalize-repos sut/default-repos)))
  (t/is (= {"foo" {:url "s3://bar"}}
           (sut/normalize-repos {"foo" {:url "s3://bar"}})))
  (t/is (= {"foo" {:invalid "invalid"}}
           (sut/normalize-repos {"foo" {:invalid "invalid"}})))

  (t/testing "replace s3p:// to s3://"
    (t/is (= {"foo" {:url "s3://bar"}}
             (sut/normalize-repos {"foo" {:url "s3p://bar"}})))
    (t/is (= {"foo" {:url "s3://bar" :no-auth true}}
             (sut/normalize-repos {"foo" {:url "s3p://bar" :no-auth true}})))))

(t/deftest snapshot?-test
  (t/are [expected in] (= expected (sut/snapshot? in))
    false ""
    false "foo"
    true "foo-snapshot"
    true "foo-SnapShot"
    true "foo-SNAPSHOT"
    true "2.5-20240101.120000-1"))


(t/deftest credentials-test
  (with-redefs [u.env/getenv #(get dummy-env %)
                u.lein/get-credential (constantly {:username "gpg-user"
                                                   :password "gpg-pass"})]
    (t/is (= {"serv3" {:url "https://three.example.com" :username "three-user" :password "three-pass"}
              "serv4" {:url "https://four.example.com" :username "lein-pass" :password "env-four"}
              "serv5" {:url "https://five.example.com" :username "gpg-user" :password "gpg-pass"}}
             (sut/credentials dummy-repos)))))

(t/deftest read-pom-test
  (t/is (= {:url "https://github.com/clj-commons/antq"
            :scm-url "https://github.com/clj-commons/antq"}
           (sut/read-pom test-pom-file)))

  (t/testing "surrounding whitespace is trimmed"
    (h/with-temp-file [f "<project><url>\n  https://example.com\n</url></project>"]
                      (t/is (= {:url "https://example.com" :scm-url nil}
                               (sut/read-pom f))))))

(t/deftest get-local-versions-test
  (let [dummy-file (io/file (io/resource "util/maven/maven-metadata-local.xml"))
        non-existing-file (io/file "/tmp" (str (UUID/randomUUID)))]
    (t/testing "valid maven-metadata-local.xml"
      (with-redefs [io/file (constantly dummy-file)]
        (t/is (= ["8.0.0" "9.0.0"]
                 (#'sut/get-local-versions* 'com.github.liquidz/antq)))))

    (t/testing "non-existing file"
      (with-redefs [io/file (constantly non-existing-file)]
        (t/is (nil? (#'sut/get-local-versions* 'com.github.liquidz/antq)))))

    (t/testing "non-existing file"
      (with-redefs [io/file (constantly dummy-file)
                    xml/parse-str (fn [& _] (throw (ex-info "test" {})))]
        (t/is (nil? (#'sut/get-local-versions* 'com.github.liquidz/antq)))))))
