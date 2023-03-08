(ns leiningen.shipit
  (:require [clojure.java.io :as io]
            [clojure.string]
            [leiningen.core.project :as proj]
            [leiningen.uberjar :as uber]))

(def sep (System/getProperty "file.separator"))
(def dupe (str sep sep))

(defn file-path [args]
  (let [p (apply str (interpose sep args))]
    (clojure.string/replace p dupe sep)))

;;small utils from spork.
(defn hock
  "A variation of spit.  hock takes the same args as spit, but ensures that
   the parents in the path exist."
  [path contents & options]
  (let [f (io/file path)]
    (do
	    (if (.exists f)
	      (io/delete-file path)
	      (io/make-parents path))
      (if (seq options)
        (spit f contents options)
        (spit f contents)))))

;;loaded from the 1.2 version of clojure.java.io, dunno why it was dropped.
(defn delete-file-recursively
  "Delete file f. If it's a directory, recursively delete all its contents.
   Raise an exception if any deletion fails unless silently is true."
  [f & [silently]]
  (let [f (io/file f)]
    (if (.isDirectory f)
      (doseq [child (.listFiles f)]
        (delete-file-recursively child silently)))
    (io/delete-file f silently)))


(defn project-map [source-path resources]
  `{:description  "shipit-wrapper"
    :name ~'shipit
    :dependencies ~'[[org.clojure/clojure "1.11.1"]]
    :profiles {:uberjar {:aot  ~'[shipit.main]
                         :main  ~'shipit.main}}
    :source-paths   ~[source-path]
    :resource-paths ~[resources]})

(defn project-clj [source-path resource-path]
  (let [pm (project-map source-path resource-path)]
    (->> `(~'defproject ~(pm :name) "1.0.0" ~@(reduce-kv (fn [acc k v] (conj acc k v)) [] (dissoc pm :name)))
         str)))

(defn empty-project [source-path resource-path]
  (with-open [rdr (clojure.java.io/reader (char-array (str source-path)))]
    (leiningen.core.project/read rdr)))

(defn set-path [src path]
  (let [pat "(def init \"\")"]
    (clojure.string/replace  src pat (str `(~'def ~'init ~path)))))

(defn get-temp-folder []
  (let [os-temp     (System/getProperty "java.io.tmpdir")
        folder-name (java.util.UUID/randomUUID)]
    (str os-temp folder-name)))

;;create a temporary folder
;;with
;;src/shipit/main.clj
;;tmp/resources/the-file.clj
;;

;;update in-memory project to add that to source-paths

(defn ^:no-project-needed
  org.clojars.joinr/shipit
  "Given a path to a clojure file, creates an uberjar with a main entrypoint
   that bundles the file and invokes load-file.  Designed to provide a simple
   means for shipping single clj scripts as uberjars."
  [_ & [path]]
  (let [filename     (-> path io/file .getName)
        root         (-> path io/file .getParent)
        temp-folder  (get-temp-folder)
        project-path (file-path [temp-folder "project.clj"])
        source-path  (file-path [temp-folder  "src"])
        main-path    (file-path [source-path  "shipit" "main.clj"])
        resources    (file-path [temp-folder  "resources"])
        res-path     (file-path [resources filename])
        _            (hock project-path (project-clj source-path resources))
        project      (leiningen.core.project/read project-path)
        main         (-> (io/resource "main.clj")
                         slurp
                         (set-path path))
        main-source  (-> (io/resource "main.clj") slurp (set-path filename))
        jartarget    (str (clojure.string/replace filename ".clj" "") ".jar")
        from-jar     (file-path [temp-folder "target" "shipit-1.0.0-standalone.jar"])
        to-jar       (if root (file-path [root jartarget]) jartarget)]
    (binding [*print-meta* false
              leiningen.core.main/*exit-process?* false
              leiningen.core.main/*debug* true]
      (try  (println ["creating temp project" main-path])
            (hock main-path main-source)
            (println ["copying resource" path res-path])
            (hock res-path (slurp path))
            (uber/uberjar project)
            (println ["copying jar" from-jar to-jar])
            (clojure.java.io/copy (io/file from-jar)
                                  (io/file to-jar))
            (finally (println [:deleting temp-folder])
                     (delete-file-recursively temp-folder)
                     print)))))

(comment
  (def testpath "c:/Users/tom/blah.clj"))
