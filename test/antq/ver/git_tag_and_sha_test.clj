(ns antq.ver.git-tag-and-sha-test
  (:require
   [antq.record :as r]
   [antq.test-report]
   [antq.util.exception :as u.ex]
   [antq.util.git :as u.git]
   [antq.ver :as ver]
   [antq.ver.git-tag-and-sha]
   [clojure.test :as t]))

(defn- dep
  [m]
  (r/map->Dependency (merge {:type :git-tag-and-sha} m)))

(t/deftest get-sorted-versions-test
  (with-redefs [u.git/tags-by-ls-remote (fn [url]
                                          (when (= "https://example.com" url)
                                            ["v3.0.0"
                                             "v2.0.0-alpha1"
                                             "invalid"
                                             "v2.0.0-alpha2"
                                             "v2.0.0"
                                             "1.0.0"]))]
    (t/is (= ["v3.0.0" "v2.0.0" "v2.0.0-alpha2" "v2.0.0-alpha1" "1.0.0"]
             (ver/get-sorted-versions (dep {:extra {:url "https://example.com"}})
                                      {}))))

  (t/testing "url is nil"
    (t/is (empty? (ver/get-sorted-versions (dep {})
                                           {})))))

(t/deftest get-sorted-versions-timeout-test
  (with-redefs [u.git/tags-by-ls-remote (fn [& _] (throw (u.ex/ex-timeout "test timeout")))]
    (let [deps (ver/get-sorted-versions (dep {:extra {:url "https://example.com"}})
                                        {})]
      (t/is (= 1 (count deps)))
      (t/is (u.ex/ex-timeout? (first deps))))))
