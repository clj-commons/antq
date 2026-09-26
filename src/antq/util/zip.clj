(ns ^:no-doc antq.util.zip
  (:require
   [antq.util.file :as u.file]
   [clojure.string :as str]
   [clojure.zip :as zip]
   [rewrite-clj.zip :as z]
   [rewrite-indented.zip :as ri.zip]))

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

(defn of-edn-file
  "rewrite-clj does not preserve OS-specific line endings, save the original line
  ending as metadata for later restoration in [[root-edn-string]]."
  [f]
  (let [orig-eol (u.file/first-eol f)
        z (z/of-file* f)]
    (with-meta z (-> z meta (assoc :antq/orig-eol orig-eol)))))

(defn root-edn-string
  [loc]
  (let [orig-eol (-> loc move-to-root meta :antq/orig-eol)
        s (z/root-string loc)]
    (if (and orig-eol (not= orig-eol "\n"))
      (str/replace s "\n" orig-eol)
      s)))

(defn of-indented-file
  "rewrite-indented does not preserve OS-specific line endings, nor does it
  parse files with windows line-endings very well. This wrapper compensates.
  Line ending is retored in [[root-indented-string]]"
  [f]
  (let [orig-eol (u.file/first-eol f)
        ;; this lib does not handle windows eols well, pre-convert to linux-style
        content (str/replace (slurp f) #"\r\n" "\n")
        z (ri.zip/of-string content)]
    (with-meta z (-> z meta (assoc :antq/orig-eol orig-eol)))))

(defn root-indented-string
  [loc]
  (let [orig-eol (-> loc move-to-root meta :antq/orig-eol)
        s (ri.zip/root-string loc)]
    (if (and orig-eol (not= orig-eol "\n"))
      (str/replace s "\n" orig-eol)
      s)))
