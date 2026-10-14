(ns ^:no-doc antq.util.async
  (:require
   [antq.util.exception :as u.ex]))

(defn fn-with-timeout
  ([f timeout-ms]
   (fn-with-timeout f timeout-ms ""))
  ([f timeout-ms timeouted-msg]
   (fn [& args]
     (let [fut (future
                 (try
                   (apply f args)
                   (catch Throwable ex
                     ex)))
           ret (deref fut timeout-ms ::timeout)]
       (when (= ::timeout ret)
         (future-cancel fut)
         (throw (u.ex/ex-timeout timeouted-msg)))
       (when (instance? Throwable ret)
         (throw ret))
       ret))))
