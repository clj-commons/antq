(ns helper.legacy
  (:require
   [babashka.fs :as fs]
   [lread.status-line :as status]))

(defn build-remnants
  []
  (let [remnants (atom [])]
    (fs/walk-file-tree
     "test/resources/"
     {:pre-visit-dir (fn [dir _attrs]
                       (if (#{".gradle" "build"} (fs/file-name dir))
                         (do (swap! remnants conj dir)
                             :skip-subtree)
                         :continue))})
    (sort @remnants)))

(defn check-for-build-remnants
  []
  (when-let [remnant (first (build-remnants))]
    (status/die 1 "Found legacy build remnant: %s\nrun `bb clean` to address." remnant)))
