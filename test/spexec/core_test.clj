(ns spexec.core-test
  (:require [clojure.test :refer :all]
            [spexec.core :refer :all]))

(def example-scenario-unique "

Scenario: create a new product
# this is a comment
When I create a new product with name \"iphone 6\" and description \"awesome phone\"
Then I receive a response with an id 56422
And a location URL
# this a second comment
# on two lines
When I invoke a GET request on location URL
Then I receive a 200 response

")

(def example-scenario-multiple "

Scenario: create a new product
# this is a comment
When I create a new product with name \"iphone 6\" and description \"awesome phone\"
Then I receive a response with an id 56422
And a location URL
# this a second comment
# on two lines
When I invoke a GET request on location URL
Then I receive a 200 response

Scenario: get product info
#test
When I invoke a GET request on location URL
Then I receive a 200 response

")

(def my-step-sentence "When I create a new product with name \"iphone 6\" and description \"awesome phone\"")
(def my-regex #"I create a new product with name \"([a-z 0-9]*)\" and description \"([a-z 0-9]*)\"")

(def example-ast [:SPEC
 [:scenario
  [:scenario_sentence "create a new product"]
  [:steps
   [:step_sentence
    [:when]
    "I create a new product with name \"iphone 6\" and description \"awesome phone\""]
   [:step_sentence [:then] "I receive a response with an id 56422"]
   [:step_sentence [:and] "a location URL"]
   [:step_sentence [:when] "I invoke a GET request on location URL"]
   [:step_sentence [:then] "I receive a 200 response"]]]
 [:scenario
  [:scenario_sentence "get product info"]
  [:steps
   [:step_sentence [:when] "I invoke a GET request on location URL"]
   [:step_sentence [:then] "I receive a 200 response"]]]]
)

(defwhen #"I create a new product with name \"([a-z 0-9]*)\" and description \"([a-z 0-9]*)\"" [name desc]
  (println "executing my when function ! with " name desc))


(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 1))))
