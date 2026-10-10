(ns antq.util.aether-test
  (:require
   [antq.util.aether :as sut]
   [antq.util.env :as u.env]
   [antq.util.leiningen :as u.lein]
   [clojure.test :as t]
   [clojure.tools.deps.util.maven :as deps.util.maven])
  (:import
   (org.apache.maven.settings
    Server
    Settings)))

(def ^:private dummy-settings
  (doto (Settings.)
    (.addServer (doto (Server.)
                  (.setId "serv1")))
    (.addServer (doto (Server.)
                  (.setId "serv2")
                  (.setUsername "two-user")
                  (.setPassword "two-pass")))))

(def ^:private dummy-repos
  {;; duplicated with dummy-settings
   "serv1" {:url "https://one.example.com"}
   ;; duplicated with dummy-settings
   "serv2" {:url "https://two.example.com"}
   ;; new to appear
   "serv3" {:url "https://three.example.com"
            :username "three-user"
            :password "three-pass"}
   ;; new to appear
   "serv4" {:url "https://four.example.com"
            :username :env
            :password :env/four}
   ;; new to appear
   "serv5" {:url "https://five.example.com"
            :creds :gpg}
   ;; should not be added because of missing username and password
   "dummy" {:url "https://dummy.example.com"}})

(def ^:private dummy-env
  {"LEIN_PASSWORD" "lein-pass"
   "FOUR" "env-four"})

(t/deftest get-maven-settings-test
  (with-redefs [deps.util.maven/get-settings (constantly dummy-settings)
                u.env/getenv #(get dummy-env %)
                u.lein/get-credential (constantly {:username "gpg-user"
                                                   :password "gpg-pass"})]
    (let [settings (sut/get-maven-settings {:repositories dummy-repos})
          servers (map #(hash-map
                         :id (.getId ^Server %)
                         :username (.getUsername %)
                         :password (.getPassword %))
                       (.getServers settings))]
      (t/is (= 5 (count servers)))

      (t/is (= #{{:id "serv1" :username nil :password nil}
                 ;; from settings.xml
                 {:id "serv2" :username "two-user" :password "two-pass"}
                 ;; from project.clj
                 {:id "serv3" :username "three-user" :password "three-pass"}
                 ;; from project.clj with environmental variable
                 {:id "serv4" :username "lein-pass" :password "env-four"}
                 ;; from profiles.clj with gpg
                 {:id "serv5" :username "gpg-user" :password "gpg-pass"}}
               (set servers))))))
