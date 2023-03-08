(ns shipit.main
  (:gen-class :main true)
  (:require [clojure.java.io :as io]))

;;replaced at build time.
(def init "")

(defn load-resource
  "Read one or more expresssions and compile them, as if by load-file without the need
   for a file."
  [path]
  (with-open [rdr (io/reader (char-array (slurp (io/resource path))))]
    (clojure.lang.Compiler/load ^java.io.Reader rdr)))

;;This is the main entry point for marathon.
;;It's a good example of a shim-class, and
;;requires some arcane features to get things
;;working, since we're creating repls on other
;;threads.
(defn -main [& args]
  (load-resource init))
