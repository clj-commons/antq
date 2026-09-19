(ns ^:no-doc antq.util.function)

(defn memoize-by
  [f key-fn]
  (let [mem (atom {})]
    (fn [m & args]
      (if-let [res (get @mem (get m key-fn))]
        res
        (let [ret (apply f m args)]
          (swap! mem assoc (get m key-fn) ret)
          ret)))))
