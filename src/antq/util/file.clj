(ns ^:no-doc antq.util.file
  (:require
   [antq.constant.project-file :as const.project-file]
   [antq.util.env :as u.env]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn- normalize-home
  [file-path]
  (if-let [home (u.env/getenv "HOME")]
    (str/replace file-path home "~")
    file-path))

(defn- normalize-path*
  [file-path]
  (-> (io/file file-path)
      (.toPath)
      (.normalize)
      (str)))

(def ^{:malli/schema [:=> [:cat 'string?] 'string?]}
  normalize-path
  (comp normalize-home
        normalize-path*))

(defn detect-project
  [file-path]
  (condp #(str/ends-with? %2 %1) file-path
    const.project-file/babashka :clojure
    const.project-file/boot :boot
    const.project-file/clojure-cli :clojure
    const.project-file/gradle :gradle
    const.project-file/leiningen :leiningen
    const.project-file/maven :pom
    const.project-file/shadow-cljs :shadow-cljs
    ::unknown))

(defn distinct-directory
  [dirs]
  (:result
   (reduce
    (fn [{:as accm :keys [fixme]} dir]
      (let [path (if (str/starts-with? dir "~")
                   dir
                   (normalize-path (.getAbsolutePath (io/file dir))))]
        (if (contains? fixme path)
          accm
          (-> accm
              (update :fixme conj path)
              (update :result conj dir)))))
    {:fixme #{} :result []}
    dirs)))
