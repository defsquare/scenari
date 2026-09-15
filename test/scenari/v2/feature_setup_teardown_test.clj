(ns scenari.v2.feature-setup-teardown-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [scenari.v2.core :as v2]
            [testit.core :refer :all]))

(def #^:private setup-teardown-state-defaults {:pre 0 :pre-scenario 0 :post 0 :post-scenario 0})
(def #^:private setup-teardown-state (atom setup-teardown-state-defaults))

(defn- inc-state! [state key]
  (swap! state update key inc))

(defn- reset-setup-teardown-state! [] (reset! setup-teardown-state setup-teardown-state-defaults))

(defn- pre-run-setup! [] (inc-state! setup-teardown-state :pre))
(defn- post-run-teardown! [] (inc-state! setup-teardown-state :post))
(defn- pre-scenario-run-setup! [] (inc-state! setup-teardown-state :pre-scenario))
(defn- post-scenario-run-teardown! [] (inc-state! setup-teardown-state :post-scenario))

(v2/defthen "I run the first scenario" [state]
  (let [{pre-run-scenario-side-effect :pre-scenario
         pre-run-side-effect :pre} @setup-teardown-state]
    (is (< 0 pre-run-scenario-side-effect 3)
        "pre-scenario-run was called more then once")
    (is (= 1 pre-run-side-effect)
        "pre-run was called once"))
  state)


(v2/defthen "I run the second scenario" [state]
  (let [{pre-run-scenario-side-effect :pre-scenario
         pre-run-side-effect :pre} @setup-teardown-state]
    (is (< 0 pre-run-scenario-side-effect 3)
        "pre-scenario-run was called more then once")
    (is (= 1 pre-run-side-effect)
        "pre-run was called once"))
  state)

(v2/deffeature setup-teardown-feature
  "Feature: setup teardown test feature
  Scenario: first scenario
    Then I run the first scenario
  Scenario: second scenario
    Then I run the second scenario
"
  {:pre-run [reset-setup-teardown-state!
             pre-run-setup!]
   :post-run [post-run-teardown!]
   :pre-scenario-run [pre-scenario-run-setup!]
   :post-scenario-run [post-scenario-run-teardown!]})


(deftest setup-teardown-run-tests 
  (testing "Using scenari runner"
    (testing "execute setup-teardown-feature successfully"
      (let [feature-result (v2/run-feature #'setup-teardown-feature)]
        (fact "returns an execution tree with :success"
              feature-result =in=> {:status :success})
        (testing "setups ran"
          (let [{pre-run-scenario-side-effect :pre-scenario
                 pre-run-side-effect :pre} @setup-teardown-state]
            (fact "pre-scenario-run was called once"
                  pre-run-scenario-side-effect => 2)
            (fact "pre-run was called once"
                  pre-run-side-effect => 1)))
        (testing "teardowns ran"
          (let [{post-run-scenario-side-effect :post-scenario
                 post-run-side-effect :post} @setup-teardown-state]
            (fact "post-scenario-run was called once"
                  post-run-scenario-side-effect => 2)
            (fact "post-run was called once"
                  post-run-side-effect => 1)))))))

