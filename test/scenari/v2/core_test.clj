(ns scenari.v2.core-test
  (:require [clojure.test :as t :refer [is]]
            [scenari.v2.core :as v2]
            [scenari.v2.test :as sc-test]
            [kaocha.type.scenari]
            [scenari.v2.glue]
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

(v2/defgiven #"My duplicated step in other ns and feature ns" [state]
             state)

(t/deftest duplicate-glues-test
  (require 'scenari.v2.glue)
  (require 'scenari.v2.other-glue.glue)
  (t/testing "Should take the step located in ns of feature"
    (t/is (= (:ns (v2/find-glue-by-step-regex {:sentence "My duplicated step in other ns and feature ns"} *ns*)) *ns*)))
  (t/testing "Should fail when step both located in others ns as same level"
    (t/is (= (try (v2/find-glue-by-step-regex {:sentence "My duplicated step in others ns"} *ns*)
                  (catch Exception _ false))
             false))))

(t/deftest deffeature-macro-test
  (t/testing "macro definition taking different feature structure"
    (t/is (some? (macroexpand '(v2/deffeature example-feature "test/scenari/v2/example.feature"))))
    (t/is (some? (macroexpand '(v2/deffeature example-feature (slurp "test/scenari/v2/example.feature")))))
    (t/is (some? (macroexpand '(v2/deffeature example-feature (first (vector (slurp "test/scenari/v2/example.feature")))))))))

(comment
  (remove-ns 'scenari.v2.core-test)
  (meta #'scenari.v2.core-test/my-feature)
  (v2/run-features)
  (v2/run-features #'scenari.v2.core-test/my-feature)
  (sc-test/run-features #'scenari.v2.core-test/toto)

  (t/run-tests)

  (krepl/test-plan)
  (krepl/run-all)
  (krepl/run :scenario)

  )