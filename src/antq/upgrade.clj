(ns ^:no-doc antq.upgrade
  (:require
   [antq.dep.github-action :as dep.gh-action]
   [antq.download :as download]
   [antq.log :as log]
   [antq.report :as report]
   [antq.util.exception :as u.ex]
   [antq.util.file :as u.file]
   [clojure.string :as str]))

(defmulti upgrader
  (fn [version-checked-dep]
    (:project version-checked-dep)))

(defmethod upgrader :default
  [dep]
  (log/error
   (format "%s: Not supported yet."
           (name (:project dep)))))

(defn- confirm
  [dep force?]
  (cond
    (and (:latest-version dep)
         force?)
    true

    (:latest-version dep)
    (do (print (format "Do you want to upgrade %s '%s' to '%s' in %s (y/n): "
                       (:name dep)
                       (:version dep)
                       (:latest-version dep)
                       (u.file/normalize-path (:file dep))))
        (flush)
        (contains? #{'y 'Y 'yes 'Yes 'YES} (read)))

    :else
    false))

(defn- normalize-version
  [dep]
  (cond
    ;; For github-action workflows, upgrade to `v2` from `v1` in the latest version named `v2.3.4`
    ;; because `v1` does not contain any dots.
    (and (= :github-action (:project dep))
         (= :github-tag (:type dep))
         (some? (:version dep))
         (some? (:latest-version dep))
         (= "uses" (dep.gh-action/get-type dep))
         (not (str/includes? (str (:version dep)) ".")))
    (update dep :latest-version #(first (str/split % #"\.")))

    :else
    dep))

(defn upgrade!
  "Return a map as follows.
  {true [upgraded-deps] false [non-upgraded deps]}"
  [deps options]
  (let [force? (or (:force options) false)
        download? (or (:download options) false)
        version-checked-deps (->> deps
                                  (filter (comp (complement u.ex/ex-timeout?)
                                                :latest-version))
                                  (map normalize-version))]
    (when (and (seq version-checked-deps)
               (not force?))
      (log/info ""))

    (let [upgrade-result (group-by
                          (fn [dep]
                            (if (confirm dep force?)
                              (if-let [upgraded-content (upgrader dep)]
                                (do (report/upgraded-dep dep options)
                                    (spit (:file dep) upgraded-content)
                                    true)
                                false)
                              false))
                          version-checked-deps)]
      (when download?
        (download/download! (get upgrade-result true)))
      upgrade-result)))
