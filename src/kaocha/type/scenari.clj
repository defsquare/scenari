(ns kaocha.type.scenari
  (:require [clojure.test :as t]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.tools.namespace.find :as ns-find]
            [kaocha.testable :as testable]
            [kaocha.hierarchy :as hierarchy]
            [kaocha.repl :as krepl]
            [scenari.v2.core :as v2]
            [scenari.v2.core :as sc]
            [scenari.v2.test])
  (:import (org.apache.commons.io FileUtils)))

(s/def :kaocha.type/scenari (s/keys :req [:kaocha/source-paths
                                          :kaocha/test-paths]))

(defn path->file "Looking path from resource or a file in file system" [path]
  (or (io/file (io/resource path))
      (io/file path)))

(defn find-feature-in-dir [path source]
  (->> path
       path->file
       ns-find/find-namespaces-in-dir
       (map #(ns-publics (symbol %)))
       (mapcat #(map meta (vals %)))
       (filter #(= (:source %) source))
       ))

(defn find-feature-in-dirs [paths source]
  (mapcat #(find-feature-in-dir % source) paths))

(defn paths->documents [paths]
  (->> paths
       (map path->file)
       (mapcat #(FileUtils/listFiles % (into-array ["story" "feature"]) true))
       (filter #(str/ends-with? (.getName %) ".feature"))
       (map (fn [feature-path] {:path   (.getPath feature-path)
                                :file   (.getName feature-path)
                                :source (slurp feature-path)}))))

(defn path->id [path]
  (-> path
      (str/replace #"/" ".")
      (str/replace #"_" "-")
      (str/replace #" " "_")
      (str/replace #"\.feature$" "")))

(defn ->id [s]
  (-> s
      str/trim
      (str/replace #"/" ".")
      (str/replace #"_" "-")
      (str/replace #" " "-")))

(defn scenario->id [scenario]
  (-> (:scenario-name scenario)
      str/trim
      (str/replace #" " "-")))

(defn scenario->testable [document scenario]
  (merge scenario
         {::testable/type :kaocha.type/scenari-scenario
          ::testable/id   (keyword (scenario->id scenario))
          ::testable/desc (:scenario-name scenario)
          ::feature       (keyword (path->id (str (:project-directory document) (:file document))))
          ::file          (str (:project-directory document) (:file document))
          }))

(defn feature->testable [testable document]
  (let [feature-meta (first (find-feature-in-dirs (::glue-paths testable) (:source document))) ;TODO handle exception when multiple deffeature match
        {{:keys [scenarios pre-run]} :feature-ast} feature-meta]
    {::testable/type         :kaocha.type/scenari-feature
     ::testable/id           (keyword (path->id (str (:project-directory document) (:file document))))
     ::testable/desc         " "                            ;; TODO fix instaparse to capture feature description
     :kaocha.test-plan/tests (mapv #(scenario->testable document %) scenarios)
     ::pre-run               pre-run}))

(defn- require-all-ns [paths]
  (->> paths
       (map path->file)
       (mapcat ns-find/find-namespaces-in-dir)
       (apply require)))

(defmethod testable/-load :kaocha.type/scenari [testable]
  (require-all-ns (::glue-paths testable))
  (let [documents (paths->documents (::feature-paths testable))]
    (-> testable
        (assoc :kaocha.test-plan/tests (mapv #(feature->testable testable %) documents)))))

(defmethod testable/-run :kaocha.type/scenari [testable test-plan]
  (let [results (testable/run-testables (:kaocha.test-plan/tests testable) test-plan)
        testable (-> testable
                     (dissoc :kaocha.test-plan/tests)
                     (assoc :kaocha.result/tests results))]
    testable))

(defmethod testable/-run :kaocha.type/scenari-feature [testable test-plan]
  (t/do-report {:type ::begin-feature})
  (doseq [{pre-run-fn :ref} (::pre-run testable)]
    (pre-run-fn))
  (let [results (testable/run-testables (:kaocha.test-plan/tests testable) test-plan)
        testable (-> testable
                     (dissoc :kaocha.test-plan/tests)
                     (assoc :kaocha.result/tests results))]
    (t/do-report {:type ::end-feature})
    testable))

(defmethod testable/-run :kaocha.type/scenari-scenario [testable test-plan]
  (t/do-report {:type :begin-scenario :scenario-name (:scenario-name testable)})
  (let [testable (sc/run-scenario testable)]
    (doseq [step (:steps testable)]
      (condp = (:status step)
        :success (t/do-report {:type :begin-step :step step})
        :fail (do
                (t/do-report {:type :begin-step :step step})
                (t/do-report {:type :step-failed :exception (:exception step)}))
        :pending nil
        nil))
    (-> testable
        (merge {:kaocha.result/count 1
                :kaocha.result/pass  (if (= (:status testable) :success) 1 0)
                :kaocha.result/fail  (if (= (:status testable) :fail) 1 0)}))))

(defmethod testable/-run :kaocha.type/scenari-step [testable test-plan]
  (let [results [(v2/run-step {} testable)]
        testable (-> testable
                     (dissoc :kaocha.test-plan/tests)
                     (assoc :kaocha.result/pass results))]
    testable))

(s/def ::glue-paths (s/coll-of string?))
(s/def ::feature-paths (s/coll-of string?))

(s/def :kaocha.type/scenari (s/keys :req [:kaocha/source-paths
                                          :kaocha/test-paths
                                          ::glue-paths]))

(s/def :kaocha.type/scenari-feature any?)
(s/def :kaocha.type/scenari-scenario any?)
(s/def :kaocha.type/scenari-step any?)



(hierarchy/derive! ::begin-feature :kaocha/begin-group)
(hierarchy/derive! ::end-feature :kaocha/end-group)

(hierarchy/derive! ::begin-scenario :kaocha/begin-test)
(hierarchy/derive! ::end-scenario :kaocha/end-test)

(hierarchy/derive! :kaocha.type/scenari :kaocha.testable.type/suite)
(hierarchy/derive! :kaocha.type/scenari-feature :kaocha.testable.type/group)
(hierarchy/derive! :kaocha.type/scenari-scenario :kaocha.testable.type/leaf)


(comment
  (in-ns 'kaocha.type.scenari)
  (krepl/run :scenario)
  (krepl/run :unit)

  (krepl/run {:config-file "tests.edn"})

  (krepl/test-plan)

  (krepl/test-plan {:tests [{:id                           :scenario
                             :type                         :kaocha.type/scenari
                             :kaocha/source-paths          ["src"]
                             :kaocha/test-paths            ["test/scenari/v2"]
                             :scenari.v2.kaocha/glue-paths ["test/scenari/v2"]}]})


  (-> (find-feature-in-dirs ["test/scenari/v2"] "Feature: foo bar kix\n\n  Scenario: create a new product\n    When I invoke a GET request on location URL\n     # this is a comment\n    When I create a new product with name \"iphone 6\" and description \"awesome phone\" with properties\n      | size | weight |\n      | 6    | 2      |\n    Then I receive a response with an id 56422\n    Then a location URL\n  Scenario: another\n    Given I foo\n\n")
      first
      (get-in [:feature-ast :scenarios]))

  (feature->testable
    {::glue-paths ["test/scenari/v2"]}
    {:path              "/Users/davidpanza/Workspace/defsquare/scenari/test/scenari/v2/example.feature",
     :project-directory "test/scenari/v2/",
     :file              "example.feature",
     :source            "Feature: foo bar kix

             Scenario: create a new product
               When I invoke a GET request on location URL
                # this is a comment
               When I create a new product with name \"iphone 6\" and description \"awesome phone\" with properties
                 | size | weight |
                 | 6    | 2      |
               Then I receive a response with an id 56422
               Then a location URL
             Scenario: another
               Given I foo

           "})

  )
