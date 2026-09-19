(ns ^:no-doc antq.util.env)

(defn getenv
  [x]
  (System/getenv x))

(defn getlong
  [env-name default-value]
  (try
    (Long/parseLong
     (or (getenv env-name)
         (str default-value)))
    (catch Exception _
      default-value)))
