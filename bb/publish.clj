(ns publish
  "Publish work that happens locally on a maintainer's workstation"
  (:require
   [babashka.tasks :as t]
   [build-config :as build-config]
   [clj-commons.ansi :as ansi]
   [clojure.string :as str]
   [helper.git :as git]
   [lread.status-line :as status]
   [version-clj.core :as v]))

;; Note to lurkers: doc updates are for AsciiDoc files.

(def ^:private re-changelog-unreleased
  #"(?ims)^== Unreleased(?<attributes>.*?)(?<unreleasedContent>$.*?)(?<nextSection>^== \d|\z)")

(defn- analyze-changelog
  "Certainly not fool proof, but should help for common mistakes"
  [{:keys! [changelog-file]}]
  (let [changelog-content (slurp changelog-file)
        valid-attrs ["[minor breaking]" "[breaking]"]
        [_ sp-attr unreleased-content :as match] (re-find re-changelog-unreleased changelog-content)]
    (if (not match)
      [{:error :section-missing}]
      (let [[_ sp attr] (re-find #"(\s*)(.*)" sp-attr)]
        (cond-> []
          (and (not (str/blank? sp-attr)) (not= " " sp))
          (conj {:error :attr-sep-not-space :sep sp})

          (and attr
               (not (str/blank? attr))
               (not (contains? (set valid-attrs) attr)))
          (conj {:error :attr-invalid :valid-attrs valid-attrs :found attr})

          ;; without any words of a reasonable min length, we consider section blank
          (not (re-find #"(?m)[\p{L}]{3,}" unreleased-content))
          (conj {:error :content-missing}))))))

(defn- analyze-versions
  [{:keys! [version released-version last-version-tag]}]
  (cond-> []
    (not (str/ends-with? version "-SNAPSHOT"))
    (conj {:error :version-not-snapshot
           :version version})

    (str/ends-with? released-version "-SNAPSHOT")
    (conj {:error :released-version-is-snapshot
           :released-version released-version})

    (not (v/older? released-version version))
    (conj {:error :released-version-gte-version
           :released-version released-version
           :version version})

    (not= last-version-tag released-version)
    (conj {:error :released-version-ne-last-version-tag
           :released-version released-version
           :last-version-tag last-version-tag})))

(defn- release-checks
  [{:keys! [version released-version last-version-tag] :as config}]
  (let [changelog-findings (reduce (fn [acc n] (assoc acc (:error n) n))
                                   {}
                                   (analyze-changelog config))
        version-findings (reduce (fn [acc n] (assoc acc (:error n) n))
                                 {}
                                 (analyze-versions config))]
    [{:check "not on fork"
      :result (if (git/not-fork? config) :pass :fail)}
     {:check "on default branch"
      :result (if (git/default-branch? config) :pass :fail)}
     {:check "no uncommitted code"
      :result (if (git/uncommitted-code?) :fail :pass)}
     {:check "no unpushed commits"
      :result (if (or (git/local-branch?) (git/unpushed-commits?)) :fail :pass)}
     {:check "in sync with project repo HEAD"
      :result (if (git/commit-matches-default-head? config) :pass :fail)}
     {:check (ansi/compose "version " [:blue version] " is SNAPSHOT")
      :result (if (:version-not-snapshot version-findings) :fail :pass)}
     {:check (ansi/compose  "released version " [:blue released-version] " is not SNAPSHOT")
      :result (if (:released-version-is-snapshot version-findings) :fail :pass)}
     {:check "released version is older than version"
      :result (if (:released-version-gte-version version-findings) :fail :pass)}
     {:check (ansi/compose "last release tag " [:blue last-version-tag]  " matches released version")
      :result (if (:released-version-ne-last-version-tag version-findings) :fail :pass)}
     {:check "changelog has unreleased section"
      :result (if (:section-missing changelog-findings) :fail :pass)}
     {:check "changelog unreleased section attributes valid"
      :result (cond
                (:section-missing changelog-findings) :skip
                (:attr-sep-not-space changelog-findings) :fail
                (:attr-invalid changelog-findings) :fail
                :else :pass)
      :msgs (cond-> []
              (:attr-sep-not-space changelog-findings)
              (conj "attribute must be prefixed with a single space")
              (:attr-invalid changelog-findings)
              (conj (let [{:keys! [valid-attrs found]} (:attr-invalid changelog-findings)]
                      (format "expected attribute to be absent or one of %s, but found: %s"
                              (str/join ", " valid-attrs) found))))}
     {:check "changelog unreleased section has content"
      :result (cond
                (:section-missing changelog-findings) :skip
                (:content-missing changelog-findings) :fail
                :else :pass)}]))

(defn- un-snapshot
  [version]
  (str/replace version #"-SNAPSHOT$" ""))

(defn- bump-version-for-release!
  [{:keys! [version version-file released-version-file]}]
  (let [release-version (un-snapshot version)]
    (spit version-file release-version)
    (spit released-version-file release-version)))

(defn- bump-version-for-dev-work!
  [{:keys! [version version-file]}]
  (let [[_ major-minor patch suffix] (re-find #"(\d+\.\d+\.)(\d+)(.*)" version)
        patch (inc (parse-long patch))
        dev-work-version (str major-minor patch suffix "-SNAPSHOT")]
    (status/line :detail "bumping version from %s to %s for future dev work" version dev-work-version)
    (spit version-file dev-work-version)))

(defn- update-file!
  [fname desc match replacement]
  (let [old-content (slurp fname)
        new-content (str/replace-first old-content match replacement)]
    (if (= old-content new-content)
      (status/die 1 "Expected to %s in %s" desc fname)
      (spit fname new-content))))

(defn- update-user-guide!
  [{:keys! [version user-guide-files]}]
  (doseq [doc-file user-guide-files]
    (status/line :detail "Applying version %s to user docs %s" version doc-file)
    (update-file! doc-file
                  "update :lib-version: adoc attribute"
                  #"(?m)^(:lib-version: )(.*)$"
                  (str "$1" version))))

(defn- yyyy-mm-dd-now-utc
  []
  (-> (java.time.Instant/now) str (subs 0 10)))

(defn- update-changelog!
  [{:keys! [version version-tag last-version-tag changelog-file github-coords]}]
  (status/line :detail "Applying version %s to changelog" version)
  (update-file! changelog-file
                "update unreleased header to release header"
                re-changelog-unreleased
                (fn [[_ attributes unreleased-content next-section]]
                  (str
                   ;; replace "Unreleased" with actual version
                   "== " version
                   ;; followed by any attributes
                   attributes
                   ;; followed by datestamp
                   " - " (yyyy-mm-dd-now-utc)
                   ;; followed by an AsciiDoc anchor for easy referencing
                   " [[" version  "]]"
                   ;; followed by section content
                   unreleased-content
                   ;; followed by link to commit log
                   (when last-version-tag
                     (str
                      "https://github.com/" github-coords "/compare/"
                      last-version-tag
                      "\\..."  ; single backslash is escape for AsciiDoc
                      version-tag
                      "[commit log]\n\n"))
                   ;; followed by next section indicator
                   next-section))))

(defn- update-changelog-for-dev-work!
  [{:keys! [changelog-file]}]
  (status/line :detail "Adding unreleased header for future dev work")
  (update-file! changelog-file
                "add new unreleased header"
                #"(?ims)(?<releasedSection>^== \d+\.\d+\.)"
                (fn [[_ released-section]]
                  (str
                   "== Unreleased\n\n"
                   released-section))))

(defn- commit-changes-for-release!
  [{:keys! [version changelog-file user-guide-files version-file released-version-file]}]
  (apply t/shell "git add"
         changelog-file
         version-file
         released-version-file
         user-guide-files)
  (t/shell "git commit -m" (str "publish: apply version " version)))

(defn- commit-changes-for-dev-work!
  [{:keys! [changelog-file version-file]}]
  (t/shell "git add" changelog-file version-file)
  (t/shell "git commit -m" "publish: prep for dev work"))

(defn- tag!
  [{:keys! [version-tag version]}]
  (status/line :detail "git tagging with: %s" version-tag)
  (t/shell "git tag" version-tag "-m" (str "For release: " version)))

(defn- push!
  []
  (t/shell "git push"))

(defn- push-tag!
  [{:keys! [version-tag]}]
  (t/shell "git push origin" version-tag))

;; task entry points

(defn pubcheck
  [_opts]
  (let [config (build-config/config)
        last-version-tag (git/last-version-tag config)
        config (assoc config :last-version-tag last-version-tag)]
    (status/line :head "Performing publish checks")
    (let [check-results (release-checks config)
          passed? (every? #(= :pass (:result %)) check-results)]
      (doseq [{:keys! [check result] :keys [msgs]} check-results]
        (status/line :detail "%s %s"
                     (case result
                       :pass (ansi/compose [:green "✓"])
                       :fail (ansi/compose [:red "x"])
                       :skip (ansi/compose [:yellow "~"]))
                     check)
        (doseq [m msgs]
          (status/line :detail "  > %s" m)))
      (when (not passed?)
        (status/die 1 "Release checks failed")))))

(defn- proceed-or-die
  [{:keys! [yes]}]
  (status/line :detail "Proceed with publish? (YES to proceed)")
  (if yes
    (status/line :detail "YES (via --yes)")
    (let [proceed-answer (str/trim (.readLine (System/console)))]
      (when (not= "YES" proceed-answer)
        (status/die 1 "Publish aborted (via \"%s\")" proceed-answer)))))

(defn publish
  {:org.babashka/cli {:spec {:no-push {:desc "Don't actually push (for local testing)"
                                       :coerce :boolean
                                       :default false}
                             :yes     {:desc "Automatically answer yes to publish prompt"
                                       :coerce :boolean
                                       :default false}}}}
  [{:keys! [no-push] :as opts}]
  (pubcheck {})
  (let [{:keys! [version lib] :as config} (build-config/config)]
    (status/line :detail "About to publish %s version %s" lib (un-snapshot version))
    (when no-push
      (status/line :warn "You've opted to not push changes to GitHub for testing purposes via --no-push"))
    (proceed-or-die opts)
    (let [pre-sha (subs (git/head-sha) 0 7)]
      (status/line :head "Calculating versions")
      (bump-version-for-release! config)
      (let [{:keys! [version version-tag last-version-tag] :as config}
            ;; important to reload to pick up version bump
            (assoc (build-config/config)
                   :last-version-tag (git/last-version-tag config))]
        (status/line :detail "Release version: %s" version)
        (status/line :detail "Release tag: %s" version-tag)
        (status/line :detail "Last release tag: %s" last-version-tag)
        (status/line :head "Updating docs")
        (update-user-guide! config)
        (update-changelog! config)
        (status/line :head "Committing changes")
        (commit-changes-for-release! config)
        (status/line :head "Tagging")
        (tag! config)
        (status/line :head "Prepping for future dev work")
        (bump-version-for-dev-work! config)
        (update-changelog-for-dev-work! config)
        (commit-changes-for-dev-work! config)
        (status/line :head "Pushing")
        (if no-push
          (status/line :warn (str/join "\n"
                                       ["As requested skipping push for local testing"
                                        "After inspecting unpushed commits & tag, YOU MUST UNDO. One way:"
                                        " git log -p %s..HEAD # to review new commits"
                                        " git tag -d %s # to delete tag"
                                        " git reset --hard %s  # revert to sha before new commits"])
                       pre-sha version-tag pre-sha)
          (do
            (push!)
            (push-tag! config)
            (status/line :detail "\nLocal work done.")
            (status/line :head "Remote work")
            (status/line :detail "The remaining work will be triggered by the release tag on CI:")
            (status/line :detail "- Publish a release jar to clojars")
            (status/line :detail "- Create a GitHub release")
            (status/line :detail "- Inform cljdoc of release")
            (status/line :detail "- Build and publish antq docker image to GitHub packages")))))))
