(ns bcbio.rnaseq.util
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.java.shell :refer [sh]]
            [version-clj.compare :refer [version-compare]]
            [me.raynes.fs :as fs]))


(defn all-keys [xs]
  "return all non-nil keys from a sequence of hash-maps"
  (->> xs (map keys) flatten (filter (comp not nil?))))

(defn fix-missing-keys
  "given a sequence of hash-maps and an optional default value,
   returns a sequence of hash-maps that have missing keys added with
   the default value. If no default is given, use nil"
  ([xs] (fix-missing-keys xs nil))
  ([xs default]
     (let [default-map (zipmap (all-keys xs) (repeat default))]
       (map (partial merge default-map) xs))))

(defn get-resource [filename]
  "return a path to an included resource"
  (try
    (.getFile (io/resource filename))
    (catch Exception e nil)))

(defn copy-resource [resource dir]
  "copy a resource from the .jar file to an external directory so
   outside programs can use it"
  (let [out-file (io/file dir (str (fs/base-name resource)))]
    (spit out-file (slurp (io/resource resource)))
    (str out-file)))

(defn swap-directory [file dir]
  "/path/to/file -> /dir/file"
  (str (io/file dir (fs/base-name file))))

(defn escape-quote [string]
  (str "\"" string "\""))


(defn base-filename [filename]
  (fs/split-ext (fs/base-name filename)))

(defn base-stem [filename]
  "get the stem of the base of the filename. (base-stem '/your/path/file.txt) => file"
  (first (base-filename filename)))


(defn get-files-in-directory [directory]
  (->> directory
       clojure.java.io/file
       .listFiles
       (filter #(.isFile %))
       (map #(str %))))

(defn has-file-extension? [file-name extension]
  (.endsWith file-name extension))

(defn filter-on-extension [files extension]
  (filter #(has-file-extension? % extension) files))

(defn get-files-with-extension [directory extension]
  (filter-on-extension (get-files-in-directory directory) extension))

(defn dirname [path]
  (str (fs/parent (fs/expand-home path)) "/"))

(defn change-extension [path extension]
  (str (dirname path) (base-stem path) extension))

(defn seq-to-factor [xs]
  "(seq-to-factor [1, 2, 3]) => 'factor(c(1, 2, 3), levels=c(1,2,3))'"
  (str "factor(c(" (clojure.string/join "," (map escape-quote xs))
       "), levels=c(" (clojure.string/join "," (map escape-quote
                                                    (vec (apply sorted-set xs)))) "))"))
(defn seq-to-rlist [xs]
  "(seq-to-rlist [1, 2, 3]) => 'c(1, 2, 3)'"
  (str "c(" (clojure.string/join "," (map escape-quote xs)) ")"))

(defn directory-exists? [dir]
  (if dir
    (.isDirectory (io/file dir))
    false))

(defn safe-makedir [dir]
  (when-not (directory-exists? dir)
    (.mkdir (io/file dir)))
  dir)

(defn file-exists? [fname]
  (if fname
    (.exists (io/as-file fname))
    false))

(defn apply-to-keys [m f & keyseq]
  "apply a function to only values of specific keys in a sequence"
  (reduce #(assoc %1 %2 (f (%1 %2))) m keyseq))

(defn catto [out & files]
  "cat files > out"
  (with-open [o (io/output-stream out)]
    (doseq [f files]
      (io/copy (io/file f) o))))

(defn replace-if [pred s match replacement]
  "replace match with replacement in string s if predicate is true"
  (if (pred s)
    (string/replace s match replacement)
    s))

(defn rmdir [dir]
  (when (directory-exists? dir)
    (fs/delete-dir dir)))

(defn- pandoc-version []
  "Return pandoc version. Returns nil if not installed."
  (try
    (-> (sh "pandoc" "-v")
        :out
        string/split-lines
        first
        (string/split #" ")
        second)
    (catch java.io.IOException e
      nil)))

(defn pandoc-supports-rmarkdown? []
  (let [version (pandoc-version)]
    (if (nil? version)
      false
      (=
       (version-compare (pandoc-version) "1.12.3") 1))))

(defn tokenize-formula [formula]
  "split an R formula into tokens"
  (string/split formula #"[\s+|\+|~|\*]") )
