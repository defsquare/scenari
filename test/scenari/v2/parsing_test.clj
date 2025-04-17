(ns scenari.v2.parsing-test
  (:require [clojure.test :refer :all]
            [scenari.core :refer [gherkin-parser sentence-parser]]))


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

(deftest feature-with-narrative-test
  (is (= (gherkin-parser "
Feature: feature with full narrative
As a user
I want to login
So that I gain access to the protected resource

Scenario: scenario 1
  Given a step")
         [:SPEC
          [:narrative
            [:as_a "user"]
            [:I_want_to "login"]
            [:so_that "I gain access to the protected resource"]]
          [:scenarios
           [:scenario
            [:scenario_sentence " scenario 1"]
            [:steps [:step_sentence [:given] [:sentence "a step"]]]]]])))

(deftest sentence-parser-test
  (testing "Parsing sentences with parameters"
    (is (= (sentence-parser "I create a new product with name \"iphone 6\" and description \"awesome phone\"")
           [:SENTENCE
            [:words "I create a new product with name "]
            [:string "iphone 6"]
            [:words " and description "]
            [:string "awesome phone"]]))
    
    (is (= (sentence-parser "I buy 42 products")
           [:SENTENCE
            [:words "I buy "]
            [:number "42"]
            [:words " products"]]))
    
    (is (= (sentence-parser "I create a new product with <product_name> and price ${price}")
           [:SENTENCE
            [:words "I create a new product with "]
            [:parameter "product_name"]
            [:words " and price "]
            [:parameter "price"]]))
    
    (is (= (sentence-parser "I create a product with map {\"name\":\"phone\",\"price\":499}")
           [:SENTENCE
            [:words "I create a product with map "]
            [:map "{\"name\":\"phone\",\"price\":499}"]]))))

(deftest unicode-character-test
  (is (= (gherkin-parser "
Scenario: scenario with unicode characters
  Given a product with name \"Téléphone\"
  When I add to cart with price €100
  Then I should see \"Produit ajouté !\"")
         [:SPEC
          [:scenarios
           [:scenario
            [:scenario_sentence " scenario with unicode characters"]
            [:steps
             [:step_sentence [:given] [:sentence "a product with name \"Téléphone\""]]
             [:step_sentence [:when] [:sentence "I add to cart with price €100"]]
             [:step_sentence [:then] [:sentence "I should see \"Produit ajouté !\""]]]]]]))
  
  (is (= (sentence-parser "a product with name \"Téléphone\"")
         [:SENTENCE
          [:words "a product with name "]
          [:string "Téléphone"]])))

(deftest empty-feature-test
  (is (= (gherkin-parser "")
         [:SPEC [:scenarios]])))

(deftest commented-feature-test
  (is (= (gherkin-parser "
# This is a comment at the top of the file
# Multi-line comment
Feature: feature with comments
# Comment after feature name
  
  # Comment before scenario
  Scenario: scenario with comments
    # Comment before step
    Given a step
    # Comment between steps
    When another step
    # Comment after steps
  
# Comment at the end")
         [:SPEC
          [:narrative]
          [:scenarios
           [:scenario
            [:scenario_sentence " scenario with comments"]
            [:steps
             [:step_sentence [:given] [:sentence "a step"]]
             [:step_sentence [:when] [:sentence "another step"]]]]]])))

(comment
  (run-tests))