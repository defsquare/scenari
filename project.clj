(defproject scenari "1.2.0"
  :description "A BDD - Behavior Driven Development - library for Clojure"
  :url "http://github.com/jgrodziski/scenari"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [instaparse "1.4.8"]
                 [com.taoensso/timbre "4.10.0"]
                 [commons-io/commons-io "2.6"]]
  :plugins [[lein-spexec "0.1.0-SNAPSHOT"]
            [lein-gorilla "0.3.4"]
            [cider/cider-nrepl "0.16.0-SNAPSHOT"]]
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"
            :distribution :repo}
  :source-paths ["src" "src/main/clojure"])

