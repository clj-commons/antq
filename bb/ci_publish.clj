(ns ci-publish
  "Publish work we invoke from GitHub Actions.
  Separated out here:
  - to make it clear what is happening on ci
  - rate of change here should be less/different than in publish namespace"
  (:require
   [babashka.deps-deploy :as dd]
   [babashka.http-client :as http]
   [babashka.tasks :as t]
   [build-config :as build-config]
   [clojure.tools.build.api :as b]
   [jar :as jar]
   [lread.status-line :as status]))

(defn- assert-on-ci
  []
  (when (not (System/getenv "CI"))
    (status/die 1 "to be run from continuous integration server only")))

(defn- ci-tag
  []
  (when (= "tag" (System/getenv "GITHUB_REF_TYPE"))
    (System/getenv "GITHUB_REF_NAME")))

(defn- analyze-ci-tag
  [{:keys! [version]}]
  (let [tag (ci-tag)]
    (if (not tag)
      (status/die 1 "CI tag not found")
      ;; git version tag and version are same syntax for antq
      (when (not= tag version)
        (status/die 1 "Lib version %s does not match version from tag %s"
                    version tag)))))

;;
;; Task entry points
;;
(defn clojars-deploy
  [_opts]
  (let [{:keys! [lib version jar-file class-dir] :as config} (build-config/config)]
    (status/line :head "Deploying lib %s version %s from jar-file %s" lib version jar-file)
    (assert-on-ci)
    (analyze-ci-tag config) ; fail on unexpected version tag
    (jar/jar {})
    (dd/deploy {:installer :remote
                :artifact jar-file
                :pom-file (b/pom-path {:lib lib :class-dir class-dir})})))

(defn github-create-release
  [_opts]
  (let [{:keys! [changelog-url version-tag] :as config} (build-config/config)]
    (status/line :head "Creating github release on tag %s\nwith changelog %s" version-tag changelog-url)
    (assert-on-ci)
    (analyze-ci-tag config)
    (t/shell "gh release create"
             version-tag
             "--title" version-tag
             "--notes" (format "[Changelog](%s#%s)" changelog-url version-tag))))

(defn cljdoc-request-build
  [_opts]
  (let [{:keys! [version lib] :as config} (build-config/config)]
    (status/line :head "Informing cljdoc of %s version %s" lib version)
    (assert-on-ci)
    (analyze-ci-tag config)
    (try
      (let [{http-status :status :keys!
             [& :status]} (http/post "https://cljdoc.org/api/request-build2"
                                     {:form-params {"project" (str lib)
                                                    "version" version}
                                      :throw false})]
        (when-not (= 200 http-status)
          (status/line :warn (str "Informing cljdoc did not seem to work, got HTTP status " http-status))))
      (catch Exception e
        (status/line :warn (str "Informing cljdoc did not seem to work: " (ex-message e)))))))

(defn docker-publish
  "Relies on various GitHub Actions env vars"
  [_opts]
  (let [{:keys! [version-tag github-coords] :as config} (build-config/config)
        registry   "ghcr.io"
        image-name github-coords
        full-image (str registry "/" image-name)]
    (status/line :head "Publishing docker image %s tag %s" full-image version-tag)
    (assert-on-ci)
    (analyze-ci-tag config)
    (status/line :detail "Logging in to registry")
    (t/shell {:in (System/getenv "GITHUB_TOKEN")}
             "docker login" registry
             "-u" (System/getenv "GITHUB_ACTOR")
             "--password-stdin")
    (status/line :detail "Building docker image")
    (t/shell "docker" "build" "."
             "-t" (str full-image ":latest")
             "-t" (str full-image ":" version-tag))
    (status/line :detail "Pushing docker image tags")
    (t/shell "docker" "push" (str full-image ":latest"))
    (t/shell "docker" "push" (str full-image ":" version-tag))))

(comment
  (:out (t/shell {:in "foobar" :out :string} "cat"))
  ;; => "foobar"

  :eoc)
