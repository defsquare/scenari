(ns scenari.v2.feature-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [scenari.v2.core :as v2]
            [scenari.v2.test :as sc-test]
            [kaocha.type.scenari]
            [scenari.v2.some-glue-ns]
            [kaocha.repl :as krepl]
            [testit.core :refer :all])
  (:import (java.io StringWriter)))

(def side-effect-atom (atom 0))
(def scenario-side-effect-atom (atom 0))

(v2/defwhen #"I foo" [state]
            (let [scenario-side-effect @scenario-side-effect-atom
                  side-effect-atom @side-effect-atom]
              (fact 1 => scenario-side-effect)
              (fact 1 => side-effect-atom)
              state))

(defn init-side-effect [] (reset! side-effect-atom 1))
(defn pre-scenario-run-side-effect [] (reset! scenario-side-effect-atom 1))
(defn post-scenario-run-side-effect [] (reset! scenario-side-effect-atom 1))

(v2/deffeature my-feature "test/scenari/v2/example.feature"
               {:pre-run           [#'init-side-effect]
                :pre-scenario-run  [#'pre-scenario-run-side-effect]
                :post-scenario-run [#'post-scenario-run-side-effect]
                :post-run          [#'init-side-effect]})

(v2/defthen "My initial state contains foo"  [state] (is (= state {:foo 1})) state)

(v2/deffeature short-feature
               "Feature: feature description
  Scenario: Scenario description
      Then My initial state contains foo"
               {:default-scenario-state {:foo 1}})

(deftest scenari-runner-test
  (testing "Using scenari runner"
    (testing "execute success feature"
      (let [[feature-result] (v2/run-features #'scenari.v2.feature-test/short-feature)]
        (fact "return an execution tree with status :success"
              feature-result =in=> {:feature   "feature description",
                                    :scenarios [{
                                                 ;:id            "3e2b3c21-2b6c-407a-90f7-f10b8f16e91e",
                                                 :pre-run       [],
                                                 :post-run      [],
                                                 :default-state {:foo 1},
                                                 :scenario-name " Scenario description",
                                                 :steps         [{:sentence-keyword :then,
                                                                  :input-state      {:foo 1},
                                                                  :raw              "Then My initial state contains foo",
                                                                  :sentence         "My initial state contains foo",
                                                                  :params           [],
                                                                  :output-state     {:foo 1},
                                                                  :status           :success,
                                                                  :order            0}],
                                                 :status        :success}],
                                    :pre-run   [],
                                    :status    :success})))))

(comment
  (remove-ns 'scenari.v2.feature-test)
  (meta #'scenari.v2.feature-test/my-feature)
  (v2/run-features)
  (v2/run-features #'scenari.v2.feature-test/my-feature)
  (sc-test/run-features #'scenari.v2.feature-test/short-feature)

  (krepl/test-plan)
  (krepl/run-all)
  (krepl/run :scenario))