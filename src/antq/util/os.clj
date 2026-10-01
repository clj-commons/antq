(ns ^:no-doc antq.util.os
  (:require
   [clojure.string :as str]))

(defn windows?
  []
  (-> (System/getProperty "os.name")
      (str/lower-case)
      (str/includes? "win")))
