(ns scenari.v2.core-test
  (:require [clojure.test :as t :refer [is]]
            [scenari.core :as v1]
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

(t/deftest generate-step-fn-test
  (t/is (= (v1/generate-step-fn {:sentence "When I create a new product with name \"iphone 6\""})
           "(defwhen \"I create a new product with name {string}\"  [state arg0]  (do \"something\"))"))
  (t/is (= (v1/generate-step-fn {:sentence "When I create a new product with name \"iphone 6\" and description \"awesome phone\""})
           "(defwhen \"I create a new product with name {string} and description {string}\"  [state arg0 arg1]  (do \"something\"))"))
  (t/is (= (v1/generate-step-fn {:sentence "When I create a new products" :tab_params [{:product_name "iPhone 6" :product_desc "telephone"}]})
           "(defwhen \"I create a new products\"  [state arg0]  (do \"something\"))"))
  (t/is (= (v1/generate-step-fn {:sentence "When I create a new product with name \"iPhone 6\" and others" :tab_params [{:product_name "iPhone 7" :product_desc "telephone"}]})
           "(defwhen \"I create a new product with name {string} and others\"  [state arg0 arg1]  (do \"something\"))"))
  (t/is (= (v1/generate-step-fn {:sentence "When I create a new product with id 1234" :tab_params [{:product_name "iPhone 7" :product_desc "telephone"}]})
           "(defwhen \"I create a new product with id {number}\"  [state arg0]  (do \"something\"))")))

(t/deftest find-sentence-params-test
  (t/testing "finding parameters in sentence"
    (is (= (v2/find-sentence-params "Given an id 1234") [{:type :value, :val 1234}]) "should return number value")
    (is (= (v2/find-sentence-params "Given an id \"1234\"") [{:type :value, :val "1234"}]) "should return string value")
    (is (= (v2/find-sentence-params "Given an id abc") []) "should return no parameters")
    (is (= (v2/find-sentence-params "Given an id 1234 and \"1234\" ") [{:type :value, :val 1234} {:type :value, :val "1234"}]) "should return multiple value")))

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
    (t/is (some? (macroexpand '(v2/deffeature example-feature (first (vector (slurp "test/scenari/v2/example.feature")))))))
    (t/is (some? (macroexpand '(v2/deffeature (symbol (str "example-feature")) (first (vector (slurp "test/scenari/v2/example.feature")))))))))

(comment
  (remove-ns 'scenari.v2.core-test)
  (meta #'scenari.v2.core-test/my-feature)
  (v2/run-features)
  (v2/run-features #'scenari.v2.core-test/my-feature)
  (sc-test/run-features #'scenari.v2.core-test/my-feature)

  (t/run-tests)

  (krepl/test-plan)
  (krepl/run-all)
  (krepl/run :scenario)

  )