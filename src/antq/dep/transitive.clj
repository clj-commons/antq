(ns ^:no-doc antq.dep.transitive
  (:require
   [antq.constant :as const]
   [antq.record :as r]
   [clojure.set :as set]
   [clojure.tools.deps :as deps]
   [clojure.tools.deps.util.maven :as deps.util.maven]))

;; ===== dep->dep-map =====
(defmulti dep->dep-map
  (fn [dep]
    (:type dep)))

(defmethod dep->dep-map :default
  [_dep]
  {})

(defmethod dep->dep-map :java
  [dep]
  {(symbol (:name dep))
   {:mvn/version (:version dep)}})

(defmethod dep->dep-map :git-sha
  [dep]
  (let [extra-url (get-in dep [:extra :url])]
    {(symbol (:name dep))
     (cond-> {:git/sha (:version dep)}
       (seq extra-url)
       (assoc :git/url extra-url))}))

(defmethod dep->dep-map :git-tag-and-sha
  [dep]
  (let [extra-url (get-in dep [:extra :url])]
    {(symbol (:name dep))
     (cond-> {:git/tag (:version dep)
              :git/sha (get-in dep [:extra :sha])}
       (seq extra-url)
       (assoc :git/url extra-url))}))

;; ===== resolved-dep->dep =====
(defn- parent-name
  [resolved]
  (let [res (some-> resolved :dependents first str)]
    (when-not res
      (println "FIXME!!!" resolved))
    res))

(defmulti resolved-dep->dep
  (fn [[_dep-name resolved] _repos]
    (:deps/manifest resolved)))

(defmethod resolved-dep->dep :default [_ _] nil)

(defmethod resolved-dep->dep :mvn
  [[dep-name resolved] repos]
  (r/map->Dependency {:name (str dep-name)
                      :type :java
                      :file ""
                      :version (:mvn/version resolved)
                      :repositories repos
                      :parent (parent-name resolved)}))

(defmethod resolved-dep->dep :deps
  [[dep-name resolved] _]
  (let [{:git/keys [tag sha url]} resolved]
    (r/map->Dependency (cond-> {:name (str dep-name)
                                :type :git-sha
                                :file ""
                                :version sha
                                :parent (parent-name resolved)}
                         (seq tag)
                         (assoc :type :git-tag-and-sha
                                :version tag
                                :extra {:sha sha})

                         :always
                         (assoc-in [:extra :url] url)))))

;; =====

(defn- deps->deps-map
  {:malli/schema [:=> [:cat r/?dependencies r/?repository] 'any?]}
  [deps repos]
  {:deps (apply merge (map dep->dep-map deps))
   :mvn/repos (merge deps.util.maven/standard-repos repos)})

(defn resolve-transitive-deps
  ([deps]
   (resolve-transitive-deps deps
                            (set (map (comp symbol :name) deps))
                            (apply merge (map :repositories deps))
                            0))
  ([deps parent-dep-names repos depth]
   (let [resolved (-> deps
                      (deps->deps-map repos)
                      (deps/resolve-deps nil))
         resolved (apply dissoc resolved parent-dep-names)]
     (if (or (empty? resolved)
             (> depth const/transitive-max-depth))
       []
       (let [child-deps (keep #(resolved-dep->dep % repos) resolved)
             child-dep-names (set (map (comp symbol :name) child-deps))
             next-parent-dep-names (set/union parent-dep-names child-dep-names)]
         (concat child-deps
                 (resolve-transitive-deps child-deps next-parent-dep-names repos (inc depth))))))))

(comment
  (def sample-dep {:type :java
                   :name "cheshire/cheshire"
                   :version "5.11.0"})
  (def sample-dep {:type :git-sha
                   :name "com.github.liquidz/antq"
                   :version "86eddb89f2a2018fd984dffaadfec13e6735e92f"})
  (def sample-dep {:type :git-tag-and-sha
                   :name "com.github.liquidz/antq"
                   :version "2.2.1017"
                   :extra {:sha "86eddb8"}})
  (def sample-dep {:type         :java
                   :name         "org.springframework.security/spring-security-core"
                   :version      "5.8.0-RC1"
                   :repositories {"repository.spring.milestone" {:url "https://repo.spring.io/milestone"}}})
  (def deps [sample-dep])

  (resolve-transitive-deps deps))
