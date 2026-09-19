(ns ^:no-doc antq.upgrade.pom
  (:require
   [antq.log :as log]
   [antq.upgrade :as upgrade]
   [antq.util.zip :as u.zip]
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.zip :as zip]))

(defn- tag-name
  [loc]
  (some-> (zip/node loc) :tag name))

(defn- tag=?
  [tag]
  (fn [loc]
    (= tag (tag-name loc))))

(defn- find-version
  [loc]
  (u.zip/find-next loc (tag=? "version") zip/right))

(defn- target-dependency?
  [loc group-id artifact-id]
  (let [{:keys [tag content]} (zip/node loc)]

    (cond
      ;; group-id
      (not (and tag
                (= "groupId" (name tag))
                (= [group-id] content)))
      false

      ;; artifact-id next to group-id
      (not (->> (zip/rights loc)
                (filter #(and (= "artifactId" (name (:tag %)))
                              (= [artifact-id] (:content %))))
                (seq)
                (some?)))
      false

      ;; exlusion
      (-> loc
          (zip/up)
          (tag-name)
          (= "exclusion"))
      false

      :else
      true)))

(defn- version-property-name
  [loc]
  (let [{:keys [content]} (some-> loc
                                  (find-version)
                                  (zip/node))]
    (some->> (first content)
             (re-seq #"\$\{(.+?)\}")
             (first)
             (second))))

(defn- edit-content
  [loc new-content]
  (zip/edit loc #(assoc % :content [new-content])))

(defn- find-properties
  [loc property-name]
  (when-let [loc (some-> (u.zip/move-to-root loc)
                         (u.zip/find-next (tag=? "properties"))
                         (zip/down)
                         (u.zip/find-next (tag=? property-name) zip/right))]
    loc))

(defn upgrade-dep
  [loc version-checked-dep]
  (let [[group-id artifact-id] (str/split (:name version-checked-dep) #"/" 2)]
    (loop [loc loc
           updated-props #{}]
      (cond
        (or (nil? loc)
            (zip/end? loc))
        (zip/root loc)

        (not (target-dependency? loc group-id artifact-id))
        (recur (zip/next loc) updated-props)

        ;; versions managed by properties
        (some? (version-property-name loc))
        (let [prop-name (version-property-name loc)]
          (if (contains? updated-props prop-name)
            (recur (zip/next loc) updated-props)
            (if-let [loc (find-properties loc prop-name)]
              ;; Re-start from the root because the location of the property is different
              ;; from the original location of dependency.
              (recur (-> loc
                         (edit-content (:latest-version version-checked-dep))
                         (u.zip/move-to-root))
                     ;; to avoid infinite loop
                     (conj updated-props prop-name))
              ;; property not found
              (do (log/info (format "Skipped to upgrade %s because the corresponding property is not found in the same POM file. It may be defined in parent POM."
                                    (:name version-checked-dep)))
                  nil))))

        ;; hard-coded versions
        :else
        (if-let [loc (find-version loc)]
          (recur (edit-content loc (:latest-version version-checked-dep))
                 updated-props)
          (do (log/info (format "Failed to upgrade %s because the version tag is not found."
                                (:name version-checked-dep)))
              nil))))))

(defmethod upgrade/upgrader :pom
  [version-checked-dep]
  (some-> (:file version-checked-dep)
          (io/input-stream)
          (xml/parse :skip-whitespace true
                     :include-node? #{:element :characters :comment})
          (zip/xml-zip)
          (upgrade-dep version-checked-dep)
          (xml/indent-str)))
