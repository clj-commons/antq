(ns antq.ver-test
  (:require
   [antq.record :as r]
   [antq.ver :as sut]
   [clojure.test :as t]))

(t/deftest under-development?-test
  (t/are [expected in] (= expected (sut/under-development? in))
    true "foo-alpha-bar"
    true "foo-beta-bar"
    true "foo-RC-bar"
    false "foo-bar"
    false ""
    false nil))

(t/deftest under-development?-real-world-versions-test
  (t/testing "pre-release markers besides alpha/beta/rc"
    (t/are [in] (= [in true] [in (sut/under-development? in)])
      "4.0.0-pre5"            ; dawcs/flow (latest release: 3.0.0)
      "0.10.0-PREVIEW-1"      ; com.gfredericks.forks.org.clojure/test.check (latest release: 0.9.0)
      "2.0.0-preview10"       ; leiningen-core/leiningen-core
      "0.1.2-dev"             ; clj-jsonrpc/clj-jsonrpc
      "2.10.0.pr1"            ; com.fasterxml.jackson.core/jackson-databind
      "4.2.0-M2"              ; org.springframework.boot/spring-boot
      "2.1.8-M1"              ; metosin/ring-swagger-ui
      "1.0-milestone-9"       ; org.gradle/gradle-tooling-api (repo.gradle.org)
      "1.0.0.cr1"             ; org.immutant/immutant
      "3.6.0.CR1"             ; org.hibernate/hibernate-core
      "0.11.0-EXPERIMENTAL"   ; com.taoensso/carmine
      "1.0.7-unstable"))      ; org.clojars.tech-product-hp/charmander
  (t/testing "a platform or release qualifier does not cancel a pre-release marker"
    (t/are [in] (= [in true] [in (sut/under-development? in)])
      "2.4-M7-groovy-5.0"     ; org.spockframework/spock-core
      "0.9.2-pre2-RELEASE"    ; com.mchange/c3p0
      "1.0.0-alpha+001"))     ; https://semver.org/#spec-item-10 example
  (t/testing "pre-releases that are already detected"
    (t/are [in] (= [in true] [in (sut/under-development? in)])
      "0.5.0-prealpha"        ; tick/tick
      "1.2.0-alphaB"          ; badjer/enlive
      "R8RC2"))               ; core-renderer/core-renderer
  (t/testing "releases"
    (t/are [in] (= [in false] [in (sut/under-development? in)])
      "3.0.0"
      "0.3.2-fix1"            ; dev.nubank/clojupyter
      "2.3-groovy-4.0"        ; org.spockframework/spock-core
      "31.0-jre"              ; com.google.guava/guava
      "2.5.1.1.KotlinM11"     ; com.fasterxml.jackson.module/jackson-module-kotlin (not milestone 11)
      "1.1.17.SP2"            ; org.jboss.weld/weld-osgi-bundle
      "5.2.25.RELEASE"        ; org.springframework/spring-core
      "1.1-incubating"        ; org.apache.crail/crail-hdfs (Apache Incubator releases)
      "1.1.1m-1.5.7"))        ; synthetic: an OpenSSL letter release, not a milestone
  (t/testing "releases where a marker only appears inside a word"
    ;; Synthetic: no real release with such a version was found on Clojars or Maven Central
    (t/are [in] (= [in false] [in (sut/under-development? in)])
      "1.0-src"
      "2.0-march"
      "1.0-alphabet"
      "1.0-prefetch"
      "1.0-mysql"
      "1.0-device"))
  (t/testing "build metadata has no precedence"
    ;; Synthetic, https://semver.org/#spec-item-10
    (t/are [in] (= [in false] [in (sut/under-development? in)])
      "1.2.3+alpha.1")))

(t/deftest  snapshot?-test
  (t/are [expected in] (= expected (sut/snapshot? in))
    true "foo-snapshot-bar"
    true "foo-SNAPSHOT-bar"
    false "foo-bar"
    false ""
    false nil))

(t/deftest latest?-test
  (t/are [expected version latest-version]
         (= expected (sut/latest?
                      (r/map->Dependency {:version version :latest-version latest-version})))
    true "1.0.0" "1.0.0"
    false "1.0.0" "2.0.0"
    true "2.0.0" "1.0.0"
    nil "1.0.0" nil
    nil nil "2.0.0"
    nil nil nil))
