(ns scenari.v2.parsing
  (:require [clojure.test :refer :all]
            [scenari.core :refer [gherkin-parser]]))


(deftest basic-feature-skeleton-test
  (is (= (gherkin-parser "
Feature: my feature
  Scenario: scenario 1
    Given a step
    When do something
    Then something happened

  Scenario: scenario 2
    Given a step")
         [:SPEC
          [:narrative]
          [:scenarios
           [:scenario
            [:scenario_sentence " scenario 1"]
            [:steps
             [:step_sentence [:given] [:sentence "a step"]]
             [:step_sentence [:when] [:sentence "do something"]]
             [:step_sentence [:then] [:sentence "something happened"]]]]
           [:scenario
            [:scenario_sentence " scenario 2"]
            [:steps
             [:step_sentence [:given] [:sentence "a step"]]]]]]
         )))

(deftest feature-with-annotation-test
  (is (= (gherkin-parser "
@Annotation1 @Annotation2
Feature: my feature
  Scenario: scenario 1
    Given a step")
         [:SPEC
          [:annotations [:annotation "Annotation1"] [:annotation "Annotation2"]]
          [:narrative]
          [:scenarios
           [:scenario
            [:scenario_sentence " scenario 1"]
            [:steps [:step_sentence [:given] [:sentence "a step"]]]]]])))

(def scenario-with-examples "
Scenario: create a new product
# this is a comment
When I create a new product with name <product_name> and description <product_desc>
Then I receive a response with an id
And a location URL
Examples:
  | product_name  | product_desc     |
  | iPhone 6      | telephone        |
  | iPhone 6+     | bigger telephone |
  | iPad          | tablet           |
")

(deftest feature-parsing-test
  (is (= (gherkin-parser "
Scenario: test example section
Given a location URL
Examples:
  | product_name  | product_desc     |
  | iPhone 6      | telephone        |
  | iPhone 6+     | bigger telephone |
  | iPad          | tablet           |
")

         [:SPEC
          [:scenarios
           [:scenario
            [:scenario_sentence " test example section"]
            [:steps [:step_sentence [:given] [:sentence "a location URL"]]]
            [:examples
             [:header " product_name  " "product_desc     "]
             [:row " iPhone 6      " "telephone        "]
             [:row " iPhone 6+     " "bigger telephone "]
             [:row " iPad          " " tablet           "]]]]]
         )))


(deftest scenario-with-examples-test
  (is (= (gherkin-parser "
Scenario: test example section
Given a location URL
Examples:
  | product_name  | product_desc     |
  | iPhone 6      | telephone        |
  | iPhone 6+     | bigger telephone |
  | iPad          | tablet           |
")

         [:SPEC
          [:scenarios
           [:scenario
            [:scenario_sentence " test example section"]
            [:steps [:step_sentence [:given] [:sentence "a location URL"]]]
            [:examples
             [:header " product_name  " "product_desc     "]
             [:row " iPhone 6      " "telephone        "]
             [:row " iPhone 6+     " "bigger telephone "]
             [:row " iPad          " " tablet           "]]]]]
         )))

(deftest scenario-with-tab-params-test
  (is (= (gherkin-parser "
Scenario: create a new product
# this is a comment
When I create a new products
  | product_name  | product_desc     |
  | iPhone 6      | telephone        |
  | iPhone 6+     | bigger telephone |
  | iPad          | tablet           |
Then I receive a response with an id")

         [:SPEC
          [:scenarios
           [:scenario
            [:scenario_sentence " create a new product"]
            [:steps
             [:step_sentence
              [:when]
              [:sentence "I create a new products"]
              [:tab_params
               [:header " product_name  " " product_desc     "]
               [:row " iPhone 6      " " telephone        "]
               [:row " iPhone 6+     " " bigger telephone "]
               [:row " iPad          " " tablet           "]]]
             [:step_sentence [:then] [:sentence "I receive a response with an id"]]]]]])))

(comment
  (run-tests))