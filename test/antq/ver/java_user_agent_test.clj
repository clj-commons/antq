(ns antq.ver.java-user-agent-test
  "Test that we send appropriate user-agent header.
  We test at java level instead of aether level because that's where bb and clojure share an abstraction."
  (:require
   [antq.test-helper :as h]
   [antq.ver.java :as sut]
   [clojure.test :as t]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test]))

(defn- cache-busting-dummy-lib
  "Because Maven caches, we generate a unique dummy lib name"
  []
  (symbol "antq-dummy" (str "-" (random-uuid))))

(def ^:private server-routes
  ;; we are only testing agent delivery, returning 404 is just fine for this goal
  {"/" {:response-status 404}})

(t/deftest antq-user-agent-is-delivered-to-maven-repo-test
  (let [lib (cache-busting-dummy-lib)]
    (h/with-server
     server-routes
     (fn [{:keys [base-url requests-atom]}]
       (h/with-user-agent-property
        nil
        (sut/get-sorted-versions-by-name* lib {:repositories {"test" {:url base-url}}} {}))
       (t/is (match? (m/seq-of {:user-agent #"antq/\d.* tools.deps/\d.*"})
                     @requests-atom))))))

(t/deftest predefined-user-agent-is-delivered-to-maven-repo-test
  (let [lib (cache-busting-dummy-lib)]
    (h/with-server
     server-routes
     (fn [{:keys [base-url requests-atom]}]
       (h/with-user-agent-property
        "user-override-agent"
        (sut/get-sorted-versions-by-name* lib {:repositories {"test" {:url base-url}}} {}))
       (t/is (match? (m/seq-of {:user-agent "user-override-agent"})
                     @requests-atom))))))
