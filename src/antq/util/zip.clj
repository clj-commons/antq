(ns ^:no-doc antq.util.zip
  (:require
   [antq.util.file :as u.file]
   [antq.util.os :as os]
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
        s (z/root-string loc)
        new-eol (when s (re-find #"\r\n|\r|\n" s))]
    (if (and orig-eol new-eol (not= orig-eol new-eol))
      (str/replace s new-eol orig-eol)
      s)))

(defn of-indented-file
  "rewrite-indented does not preserve OS-specific line endings, nor does it
  parse files with windows line-endings very well, nor does it emit line-endings
  on windows consistently. This wrapper compensates.
  Line ending is retored in [[root-indented-string]]"
  [f]
  (let [content (slurp f)
        orig-eol (when content (re-find #"\r\n|\r|\n" content))
        ;; rewrite-indented does not handle windows eols well, pre-convert to linux-style
        content (if (= "\r\n" orig-eol)
                  (str/replace content "\r\n" "\n")
                  content)
        z (ri.zip/of-string content)]
    (with-meta z (-> z meta (assoc :antq/orig-eol orig-eol)))))

(defn root-indented-string
  [loc]
  (let [orig-eol (-> loc move-to-root meta :antq/orig-eol)
        s (ri.zip/root-string loc)
        new-eol (when s (re-find #"\r\n|\r|\n" s))]
    (cond
      (and orig-eol new-eol (os/windows?))
      ;; rewrite-indented ouputs a \r\n and and a terminating \n on windows, compensate
      (str/replace s #"\r\n|\r|\n" orig-eol)

      (and orig-eol new-eol (not= orig-eol new-eol))
      (str/replace s new-eol orig-eol)

      :else
      s)))
