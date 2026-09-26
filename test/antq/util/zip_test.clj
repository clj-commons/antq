(ns antq.util.zip-test
  (:require
   [antq.util.zip :as sut]
   [babashka.fs :as fs]
   [clojure.test :as t]
   [rewrite-clj.zip :as z]))

(t/deftest move-to-root-test
  (let [loc (z/of-string "(foo (bar (baz)))")
        loc' (-> loc
                 (z/next)
                 (z/next)
                 (sut/move-to-root))]
    (t/is (= (z/sexpr loc)
             (z/sexpr loc')))))

(t/deftest find-next-test
  (t/is (= '(foo (hello (baz)))
           (-> (z/of-string "(foo (bar (baz)))")
               (sut/find-next #(= 'bar (and (z/sexpr-able? %)
                                            (z/sexpr %))))
               (z/edit (constantly 'hello))
               (sut/move-to-root)
               (z/sexpr))))

  (t/is (nil? (-> (z/of-string "(foo (bar (baz)))")
                  (sut/find-next #(= 'unknown (and (z/sexpr-able? %)
                                                   (z/sexpr %))))))))

(t/deftest edn-update-preserves-line-endings-test
  (doseq [content ["{:deps {foo/bar {:mvn/version \"1.2.3\"}}\r\n}\r\n"
                   "{:deps {foo/bar {:mvn/version \"1.2.3\"}}\n}\n"]]
    (fs/with-temp-dir [d]
      (let [f (fs/file d "shadow-cljs.edn")]
        (spit f content)
        (t/is (= content (-> f
                             sut/of-edn-file
                             z/down z/down z/right
                             sut/root-edn-string))
              content)))))

(t/deftest indented-update-preserves-windows-line-endings-test
  (doseq [content ["foo\r\n  hello\r\n    bar\r\n"
                   "foo\n  hello\n    bar\n"]]
    (fs/with-temp-dir [d]
      (let [f (fs/file d "shadow-cljs.edn")]
        (spit f content)
        (t/is (= content (-> f
                             sut/of-indented-file
                             z/down z/down
                             sut/root-indented-string))
              (pr-str content))))))
