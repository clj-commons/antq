(ns build-config
  (:require
   [clojure.string :as str]))

(defn config
  ([] (config {}))
  ([{override-version :version}]
   (let [lib 'com.github.liquidz/antq
         github-coords "clj-commons/antq"
         version-file (format "resources/%s/VERSION" lib)
         version (or override-version (-> (slurp version-file) str/trim))
         default-branch "main"
         released-version-file (format "resources/%s/RELEASED_VERSION" lib)
         changelog-file "CHANGELOG.adoc"]
     {;; project specific
      :lib lib
      :default-branch default-branch
      :description "Point out your outdated dependencies"
      :license-name "Eclipse Public License - v 2.0"
      :license-url "https://www.eclipse.org/legal/epl-2.0/"
      :github-coords github-coords
      :version version
      :version-tag version ; same for Antq
      :main 'antq.core ; for uberjar
      :changelog-file changelog-file
      :user-guide-file "README.adoc"

      ;; derived and/or less likely to change
      :url (str "https://github.com/" github-coords)
      :scm-connection (format "scm:git:https://github.com/%s.git" github-coords)
      :scm-dev-connection (format "scm:git:ssh://git@github.com/%s.git" github-coords)
      :class-dir "target/classes"
      :jar-file (format "target/%s.jar" (name lib)) ; we omit version for a constant knowable filename
      :version-file version-file
      :released-version-file released-version-file
      :released-version (-> (slurp released-version-file) str/trim)
      :uberjar-file (format "target/%s-standalone.jar" (name lib)) ; we omit version for a constant knowable filename
      :changelog-url (format "https://github.com/%s/blob/%s/%s" github-coords default-branch changelog-file)})))
