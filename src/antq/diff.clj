(ns ^:no-doc antq.diff)

(defmulti get-diff-url
  (fn [version-checked-dep]
    (:type version-checked-dep)))

(defmethod get-diff-url :default
  [_dep]
  nil)
