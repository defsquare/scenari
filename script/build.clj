(ns build
  (:require
    [clojure.tools.build.api :as b]))

(def lib 'net.clojars.defsquare/scenari)
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))


(defn clean [_]
  (b/delete {:path "target"}))

(defn uberjar [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file (format "target/%s.jar" (name lib))
           :basis basis
           :main 'scenari.v2.core}))
