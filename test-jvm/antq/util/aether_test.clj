(ns antq.util.aether-test
  (:require
   [antq.test-report]
   [antq.util.aether :as sut]
   [clojure.test :as t]
   [clojure.tools.deps.util.maven :as deps.util.maven])
  (:import
   (org.apache.maven.settings
    Mirror
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

(t/deftest resolved-repositories-test
  (let [settings (doto (Settings.)
                   (.addMirror (doto (Mirror.)
                                 (.setId "central-mirror")
                                 (.setMirrorOf "central")
                                 (.setUrl "https://mirror.example.com/")))
                   (.addServer (doto (Server.)
                                 (.setId "clojars")
                                 (.setUsername "settings-user")
                                 (.setPassword "settings-pass"))))
        repos (into {"own" {:url "https://own.example.com/" :username "own-user" :password "own-pass"}}
                    (map #(vector (str "r" %) {:url (str "https://r" % ".example.com/")}))
                    (range 8))]
    (with-redefs [deps.util.maven/get-settings (constantly settings)]
      (t/testing "central and clojars come first, like in tools.deps; mirrors and credentials apply"
        (t/is (= [{:id "central-mirror" :url "https://mirror.example.com/"}
                  {:id "clojars" :url "https://repo.clojars.org/" :username "settings-user" :password "settings-pass"}]
                 (take 2 (sut/resolved-repositories
                          "foo/bar" "1.0"
                          {:repositories (merge repos {"central" {:url "https://repo1.maven.org/maven2/"}
                                                       "clojars" {:url "https://repo.clojars.org/"}})}))))
        (t/is (= {:id "own" :url "https://own.example.com/" :username "own-user" :password "own-pass"}
                 (some #(when (= "own" (:id %)) %)
                       (sut/resolved-repositories "foo/bar" "1.0" {:repositories repos}))))))))
