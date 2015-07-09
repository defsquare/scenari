(defproject spexec "1.1.0"
              :description "A BDD - Behavior Driven Development - library for Clojure"
              :url "http://github.com/zenmodeler/spexec"
              :dependencies [[org.clojure/clojure "1.6.0"]
                             [instaparse "1.4.1"]
                             [com.taoensso/timbre "3.4.0"]
                             [commons-io/commons-io "2.4"]]
              :plugins [[lein-spexec "0.1.0-SNAPSHOT"]
                        [lein-gorilla "0.3.4"]
                        [cider/cider-nrepl "0.9.1"]]
              :license {:name "MIT License"
                        :url "http://opensource.org/licenses/MIT"
                        :distribution :repo}
              :source-paths ["src" "src/main/clojure"])

