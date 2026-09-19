(ns ^:no-doc antq.util.async
  (:require
   [antq.util.exception :as u.ex]
   [clojure.core.async :as async]))

(defn fn-with-timeout
  ([f timeout-ms]
   (fn-with-timeout f timeout-ms ""))
  ([f timeout-ms timeouted-msg]
   (fn [& args]
     (let [ch (async/chan)]
       (async/pipe (async/thread
                     (try
                       (apply f args)
                       (catch Throwable ex
                         ex)))
                   ch)
       (let [ret (-> [ch
                      (async/go
                        (async/<! (async/timeout timeout-ms))
                        (u.ex/ex-timeout timeouted-msg))]
                     (async/alts!!)
                     (first))]
         (when (instance? Throwable ret)
           (throw ret))
         ret)))))
