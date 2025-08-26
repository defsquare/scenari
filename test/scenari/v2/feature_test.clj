(ns ^:integration scenari.v2.feature-test
  (:require [clojure.test :as t :refer [is]]
            [scenari.core :as v1]
            [scenari.v2.core :as v2]
            [scenari.v2.test :as sc-test]
            [kaocha.type.scenari]
            [scenari.v2.some-glue-ns]
            [kaocha.repl :as krepl]
            [testit.core :refer :all]))

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

(v2/deffeature my-feature-2
               "Feature: foo bar kix
  Scenario: create a new product
      Then My initial state contains foo"
               {:default-scenario-state {:foo 1}})

(comment
  (remove-ns 'scenari.v2.feature-test)
  (meta #'scenari.v2.feature-test/my-feature)
  (v2/run-features)
  (v2/run-features #'scenari.v2.feature-test/my-feature)
  (sc-test/run-features #'scenari.v2.feature-test/my-feature)

  (krepl/test-plan)
  (krepl/run-all)
  (krepl/run :scenario)

  )