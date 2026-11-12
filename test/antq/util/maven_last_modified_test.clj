(ns antq.util.maven-last-modified-test
  (:require
   [antq.test-helper :as h]
   [antq.test-report]
   [antq.util.maven :as sut]
   [clojure.test :as t]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test])
  (:import
   java.time.Instant))

(t/deftest last-modified-test
  (h/with-server
   {"/ok"         {:response-headers {"Last-Modified" "Tue, 20 Oct 2026 08:20:13 GMT"}}
    "/no-header"  {}
    "/bad-header" {:response-headers {"Last-Modified" "yesterday"}}
    "/missing"    {:response-status 404
                   :response-headers {"Last-Modified" "Tue, 20 Oct 2026 08:20:13 GMT"}}}
   (fn [{:keys [base-url requests-atom]}]
     (h/with-user-agent-property
      nil
      (reset! requests-atom [])
      (t/is (= (Instant/parse "2026-10-20T08:20:13Z")
               (sut/last-modified (str base-url "/ok") nil)))
      (t/is (match? (m/nested-equals
                     [{:path "/ok"
                       :method :head
                       :auth nil
                       :user-agent #"antq/\d.* tools.deps/\d.*"}])
                    @requests-atom))

      (t/testing "basic auth"
        (reset! requests-atom [])
        (sut/last-modified (str base-url "/ok") {:username "user" :password "pass"})
        (t/is (match? (m/nested-equals
                       [{:path "/ok"
                         :method :head
                         :auth "Basic dXNlcjpwYXNz"
                         :user-agent #"antq/\d.* tools.deps/\d.*"}])
                      @requests-atom)))

      (t/testing "no usable date"
        (reset! requests-atom [])
        (t/is (nil? (sut/last-modified (str base-url "/no-header") nil)))
        (t/is (match? (m/nested-equals
                       [{:path "/no-header"
                         :method :head
                         :auth nil
                         :user-agent #"antq/\d.* tools.deps/\d.*"}])
                      @requests-atom))
        (t/is (nil? (sut/last-modified (str base-url "/bad-header") nil)))
        (t/is (nil? (sut/last-modified (str base-url "/missing") nil)))))

     (t/testing "user-agent affected by aether system property"
       (reset! requests-atom [])
       (h/with-user-agent-property
        "user-agent-override"
        (sut/last-modified (str base-url "/ok") nil))
       (t/is (match? (m/nested-equals
                      [{:path "/ok"
                        :method :head
                        :auth nil
                        :user-agent "user-agent-override"}])
                     @requests-atom)))))

  (t/testing "unreachable or unsupported repository"
    (t/is (nil? (sut/last-modified "http://127.0.0.1:1/ok" nil)))
    (t/is (nil? (sut/last-modified "s3://bucket/ok" nil)))))
