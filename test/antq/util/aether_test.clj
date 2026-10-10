(ns antq.util.aether-test
  (:require
   [antq.util.aether :as sut]
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

(t/deftest get-maven-settings-test
  (with-redefs [deps.util.maven/get-settings (constantly dummy-settings)]
    (let [settings (sut/get-maven-settings
                    {:repositories {"serv2" {:url "https://two.example.com"
                                             :username "lein-user"
                                             :password "lein-pass"}
                                    "serv3" {:url "https://three.example.com"
                                             :username "three-user"
                                             :password "three-pass"}}})
          servers (map #(hash-map
                         :id (.getId ^Server %)
                         :username (.getUsername %)
                         :password (.getPassword %))
                       (.getServers settings))]
      (t/is (= #{{:id "serv1" :username nil :password nil}
                 ;; settings.xml wins over project.clj
                 {:id "serv2" :username "two-user" :password "two-pass"}
                 {:id "serv3" :username "three-user" :password "three-pass"}}
               (set servers))))))
