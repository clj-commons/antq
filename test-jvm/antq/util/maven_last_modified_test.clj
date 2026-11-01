(ns antq.util.maven-last-modified-test
  (:require
   [antq.test-report]
   [antq.util.maven :as sut]
   [clojure.test :as t])
  (:import
   (com.sun.net.httpserver
    HttpHandler
    HttpServer)
   java.net.InetSocketAddress
   java.time.Instant))

(def ^:private requests (atom []))

(defn- respond
  [status last-modified]
  (reify HttpHandler
    (handle [_ exchange]
      (swap! requests conj {:method (.getRequestMethod exchange)
                            :auth (.getFirst (.getRequestHeaders exchange) "Authorization")})
      (when last-modified
        (.add (.getResponseHeaders exchange) "Last-Modified" last-modified))
      (.sendResponseHeaders exchange status -1)
      (.close exchange))))

(defn- with-server
  [f]
  (let [server (doto (HttpServer/create (InetSocketAddress. "127.0.0.1" 0) 0)
                 (.createContext "/ok" (respond 200 "Tue, 20 Oct 2026 08:20:13 GMT"))
                 (.createContext "/no-header" (respond 200 nil))
                 (.createContext "/bad-header" (respond 200 "yesterday"))
                 (.createContext "/missing" (respond 404 "Tue, 20 Oct 2026 08:20:13 GMT"))
                 (.start))]
    (try
      (f (str "http://127.0.0.1:" (.getPort (.getAddress server))))
      (finally
        (.stop server 0)))))

(t/deftest last-modified-test
  (with-server
    (fn [base-url]
      (reset! requests [])
      (t/is (= (Instant/parse "2026-10-20T08:20:13Z")
               (sut/last-modified (str base-url "/ok") nil)))
      (t/is (= [{:method "HEAD" :auth nil}] @requests))

      (t/testing "basic auth"
        (reset! requests [])
        (sut/last-modified (str base-url "/ok") {:username "user" :password "pass"})
        (t/is (= [{:method "HEAD" :auth "Basic dXNlcjpwYXNz"}] @requests)))

      (t/testing "no usable date"
        (t/is (nil? (sut/last-modified (str base-url "/no-header") nil)))
        (t/is (nil? (sut/last-modified (str base-url "/bad-header") nil)))
        (t/is (nil? (sut/last-modified (str base-url "/missing") nil))))))

  (t/testing "unreachable or unsupported repository"
    (t/is (nil? (sut/last-modified "http://127.0.0.1:1/ok" nil)))
    (t/is (nil? (sut/last-modified "s3://bucket/ok" nil)))))
