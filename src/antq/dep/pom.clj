(ns ^:no-doc antq.dep.pom
  "Clojure CLI"
  (:require
   [antq.constant.project-file :as const.project-file]
   [antq.record :as r]
   [antq.util.dep :as u.dep]
   [antq.util.maven :as u.mvn]
   [antq.util.xml :as u.xml]
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.tools.deps.extensions.pom :as ext.pom])
  (:import
   java.io.File
   org.apache.maven.model.Repository))

(defn extract-repos-from-xml
  [xml]
  (->> xml
       (u.xml/get-tags :repository)
       (map #(u.xml/get-values [:id :url] (:content %)))
       (reduce #(assoc %1 (first %2) {:url (second %2)}) {})))

(defn extract-deps-from-xml-string
  [file-path pom-xml-content-str]
  (let [xml (->> pom-xml-content-str
                 xml/parse-str
                 xml-seq)
        repos (extract-repos-from-xml xml)]
    (->> (u.xml/get-tags :dependency xml)
         (map #(u.xml/get-values [:groupId :artifactId :version] (:content %)))
         (filter (fn [[_ _ version]] (seq version)))
         (map (fn [[group-id artifact-id version]]
                (r/map->Dependency {:project :pom
                                    :type :java
                                    :file file-path
                                    :name (str group-id "/" artifact-id)
                                    :version version
                                    :repositories repos}))))))

(defn extract-deps
  [^String file-path ^File file]
  (try
    (let [config {:mvn/repos u.mvn/default-repos}
          model (ext.pom/read-model-file file config)
          repos (reduce (fn [accm ^Repository repo]
                          (assoc accm (.getId repo) {:url (.getUrl repo)}))
                        {} (.getRepositories model))]
      (for [[dep-name attr] (ext.pom/model-deps model)]
        (r/map->Dependency {:project :pom
                            :type :java
                            :file file-path
                            :name (str dep-name)
                            :version (:mvn/version attr)
                            :repositories repos})))
    (catch Exception _
      ;; Fall back to pasing XML
      (extract-deps-from-xml-string
       file-path
       (slurp file)))))

(defn load-deps
  {:malli/schema [:function
                  [:=> :cat [:maybe r/?dependencies]]
                  [:=> [:cat 'string?] [:maybe r/?dependencies]]]}
  ([] (load-deps "."))
  ([dir]
   (let [file (io/file dir const.project-file/maven)]
     (when (.exists file)
       (extract-deps (u.dep/relative-path file)
                     file)))))
