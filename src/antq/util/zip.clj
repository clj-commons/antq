(ns ^:no-doc antq.util.zip
  (:require
   [clojure.zip :as zip]
   [rewrite-clj.zip :as z]))

(defn move-to-root
  [loc]
  (loop [loc loc]
    (if-let [loc' (z/up loc)]
      (recur loc')
      loc)))

(defn find-next
  ([loc pred]
   (find-next loc pred zip/next))
  ([loc pred next-fn]
   (loop [loc loc]
     (if (or (nil? loc)
             (zip/end? loc))
       nil
       (if (pred loc)
         loc
         (recur (next-fn loc)))))))
