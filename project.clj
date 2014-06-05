(defproject bcbio.rnaseq "0.1.1a-SNAPSHOT"
  :description "Run mutiple DE callers on RNA-seq data"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [de.ubercode.clostache/clostache "1.4.0"]
                 [incanter "1.5.5"]
                 [incanter/incanter-io "1.5.5"]
                 [me.raynes/fs "1.4.4"]
                 [clj-yaml "0.4.0"]
                 [clj-biosequence "0.1.4-SNAPSHOT"]
                 [org.clojure/math.numeric-tower "0.0.4"]
                 [org.clojure/data.json "0.2.2"]
                 [clj-http "0.7.5"]
                 [net.mikera/vectorz-clj "0.22.0"]
                 [org.clojure/math.combinatorics "0.0.4"]
                 [org.clojure/tools.cli "0.3.0"]
                 [bcbio.run "0.0.1-SNAPSHOT"]
                 [clj-http "0.7.8"]]
  :profiles {:dev {:dependencies [[midje "1.6.3"]]}}
  :plugins [[lein-midje "3.1.3"]]
  :resource-paths ["resources"]
  :jvm-opts ["-Xmx2g"]
  :aot [bcbio.rnaseq.core]
  :main bcbio.rnaseq.core
  :keep-non-project-classes true)
