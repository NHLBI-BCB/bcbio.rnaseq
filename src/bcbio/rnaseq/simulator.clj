(ns bcbio.rnaseq.simulator
  (:require [clojure.test :as test]
            [bcbio.rnaseq.htseq-combine :as counts]
            [bcbio.rnaseq.util :as util]
            [incanter.stats :as stats]
            [clojure.java.io :as io]
            [clojure.math.numeric-tower :as math]
            [incanter.core :as incanter]
            [me.raynes.fs :as fs]
            [incanter.distributions :as dist]
            [incanter.charts :as charts]
            [clojure.core.matrix :as mat])
  (:use [incanter.core :only [pow div sqrt mult plus bind-columns nrow ncol
                              conj-cols to-dataset col-names save]]
        [incanter.io :only [read-dataset]]))

(defn matmin [M]
  (apply min (flatten M)))

(defn matmax [M]
  (apply max (flatten M)))


(def default-library-size 20e6)
(def default-fold-changes [1.05 1.1 1.5 2 4])

(def not-nil? (complement nil?))

(def default-count-file (util/get-resource "test-analysis/combined.counts"))
(def default-proportion-file (util/get-resource "comparisons/baseprop.tsv"))
(def default-count-matrix
  (incanter/to-matrix
   (incanter/sel
    (read-dataset default-count-file :delim \tab :header true)
    :except-cols :id)))

(def default-proportions (incanter/to-matrix
                          (read-dataset default-proportion-file :delim \tab)))


(defn bcv0 [mu0 bcv]
  (plus bcv (div 1 (sqrt mu0))))

(defn draw-gamma [shape rate]
  "draw from gamma distribution"
  (dist/draw (dist/gamma-distribution shape rate)))

(defn draw-poisson [lambda]
  (dist/draw (dist/poisson-distribution lambda)))

(defn size [M]
  (* (ncol M) (nrow M)))

(defn sample-inv-chisq [n]
  (take n (repeatedly #(sqrt (/ 40 (dist/draw (dist/chisq-distribution 40)))))))

(defn add-biological-variation [xs bcv]
  (let [bcv0 (->> xs sqrt (div 1) (plus bcv))]
    (mult bcv0 (sample-inv-chisq (count bcv0)))))


(defn add-biological-noise [mu0 bcv]
  (incanter/matrix (mult (flatten (bcv0 mu0 bcv)) (sample-inv-chisq (size mu0)))
                   (ncol mu0)))

(defn sample-gamma [mu0 bcv]
  (let [bv (add-biological-noise mu0 bcv)
        shape (div 1 (incanter/pow bv 2))
        rate (div shape mu0)]
    (incanter/matrix (map draw-gamma (flatten shape) (flatten rate))
                     (ncol mu0))))


(defn generate-counts [xs library-size]
  (mult xs library-size))


(defn- fold-changes
  ([] (fold-changes default-fold-changes))
  ([xs] (concat xs (map #(/ 1 %) xs))))

(defn- row-means [M]
  (map stats/mean M))

(defn- row-sd [M]
  (map stats/sd M))

(defn- safe-log2 [M]
  "Add a small positive number to avoid logging 0 numbers"
  (incanter/log2 (incanter/plus M 0.01)))

(defn- proportion [xs]
  (let [augmented (plus 2e-8 xs)]
    (incanter/div augmented (apply + augmented))))

(defn- base-proportion [count-table]
  (-> count-table row-means proportion))

(defn- get-fold-changes [n nde]
  "return a vector of fold changes of length n with nde elements
   changed for each fold change"
  (shuffle
   (take n (concat
            (->> (fold-changes) (repeat nde) flatten) (repeat 1)))))

(defn generate-clean-counts [fc1 props n library-size]
  (let [raw (generate-counts props library-size)
        counts1 (mult fc1 raw)]
    (apply incanter/bind-columns
           (concat (repeat n counts1) (repeat n raw)))))

(defn make-gene-ids [n]
  (map #(str "gene_" (range n))))

(defn make-sample-ids [n]
  (map #(str "sample_" (range n))))

(defn prep-counts-matrix [M rnames cnames]
  (col-names
   (to-dataset (conj-cols rnames M)) (cons "id" cnames)))

(defn prep-bare-matrix [M]
  (let [rnames (map #(str "gene_" %) (range (nrow M)))
        cnames (map #(str "sample_" %) (range (ncol M)))]
    (prep-counts-matrix M rnames cnames)))

(defn prep-score-matrix [scores]
  (let [rnames (map #(str "gene_" %) (range (count scores)))]
    (col-names
     (to-dataset (conj-cols rnames (map float (map incanter/log2 scores))))
     ["id" "correct"])))


(defn write-matrix [M out-file]
  (if (.exists (io/as-file out-file))
    out-file
    (do
      (save M out-file :delim "\t")
      out-file)))

(defn simulate-genes [fcs sample-size library-size]
  (let [props default-proportions
        mu0 (generate-clean-counts fcs props sample-size
                                   (* 1e6 library-size))
        BCV0 (bcv0 mu0 0.2)
        BCV (add-biological-noise mu0 0.2)
        shape (div 1 (pow BCV 2))
        rate (div shape mu0)
        mu (incanter/matrix (map draw-gamma (flatten shape) (flatten rate))
                                 (ncol mu0))]
    (incanter/to-dataset
     (incanter/matrix
      (map int (map draw-poisson (flatten mu))) (ncol mu)))))

(defn simulate-and-write [out-dir sample-size library-size]
  (let [out-file (str (fs/file out-dir "sim.counts"))
        props default-proportions
        score-file (str (fs/file out-dir "sim.scores"))
        fcs (get-fold-changes (count props) (int (* 0.01 (count props))))
        counts (simulate-genes fcs sample-size library-size)]
    (util/safe-makedir out-dir)
    (write-matrix (prep-score-matrix fcs) score-file)
    (write-matrix (prep-bare-matrix counts) out-file)
    out-file))
