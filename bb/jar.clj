(ns jar
  (:require
   [babashka.fs :as fs]
   [build-config :as build-config]
   [clojure.tools.build.api :as b]
   [lread.status-line :as status]))

(defn- write-pom
  [{:keys! [lib version description
            class-dir basis url
            scm-connection scm-dev-connection
            version-tag
            license-name license-url]}]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                ;; :src-dirs and :resource-dirs end up under <build> in pom.xml,
                ;; not sure of usefulness, but continue to include them
                :src-dirs ["src"]
                :resource-dirs ["resources"]
                :scm {:url url
                      :connection scm-connection
                      :developerConnection scm-dev-connection
                      :tag version-tag}
                :pom-data [[:description description]
                           [:url url]
                           [:licenses
                            [:license
                             [:name license-name]
                             [:url license-url]]]
                           [:properties
                            [:project.build.sourceEncoding "UTF-8"]]]}))

(defn jar
  "Build library jar file."
  ;; accepts opts only for call from install
  [opts]
  (status/line :head "Jar")
  (let [{:keys! [class-dir jar-file version] :as opts}
        (-> (build-config/config opts)
            (assoc :basis (b/create-basis {})))]
    (status/line :detail "creating jar %s for version %s" jar-file version)
    (fs/delete-tree class-dir)
    (fs/delete-if-exists jar-file)
    (write-pom opts)
    (b/copy-dir {:src-dirs ["src" "resources"]
                 :target-dir class-dir})
    (b/jar {:class-dir class-dir
            :jar-file jar-file})
    opts))

(defn uberjar
  [opts]
  (status/line :head "Uberjar")
  (let [{:keys! [class-dir basis main uberjar-file version] :as opts}
        (-> (build-config/config opts)
            (assoc :basis (b/create-basis
                           {;; NOTE: To include org.slf4j/slf4j-nop to disable logging
                            :aliases [:nop]})))]
    (status/line :detail "creating uberjar %s for version %s" uberjar-file version)
    (fs/delete-tree class-dir)
    (fs/delete-if-exists uberjar-file)
    (write-pom opts)
    (b/copy-dir {:src-dirs ["src/antq" ; our way to exclude src/leiningen
                            "resources"]
                 :target-dir class-dir})
    (b/compile-clj {:basis basis
                    :src-dirs ["src/antq"]
                    :class-dir class-dir})
    (b/uber {:class-dir class-dir
             :uber-file uberjar-file
             :basis basis
             :main main})
    opts))
