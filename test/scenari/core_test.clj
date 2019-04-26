(ns scenari.core-test
  (:require [clojure.test :as test]
            [scenari.core :refer :all]
            [scenari.utils :as utils])
  (:import [java.util.UUID]))


(test/deftest test-fn-name-from-regex-str
  (test/is (= "lutilisateur--existe-dans-le-repository" (remove-non-word-character "l'utilisateur (.*) existe dans le repository")))
  (test/is (= "lutilisateur-azAZ-existe-dans-le-repository" (remove-non-word-character "l'utilisateur ([a-zA-Z]) existe dans le repository"))))

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
Narrative:
As a user
I want to login
So that I gain access to the protected resource

# this is a comment in between

Scenario: create a new product
# this is a comment
When I create a new product with name \"iphone 6\" and description 'awesome phone'
Then I receive a response with an id 56422
And a location URL
# this a second comment
# on two lines
When I invoke a GET request on location URL
Then I receive a 200 response

# comment between scenarios

Scenario: get product info
#test
When I invoke a GET request on location URL
Then I receive a 200 response

")

(def my-step-sentence "When I create a new product with name 'iphone 6' and description 'awesome phone'")
(test/deftest test-one-sentence []
  (gherkin-parser my-step-sentence))

(def my-regex #"I create a new product with name \"([a-z 0-9]*)\" and description \"([a-z 0-9]*)\"")

(def example-ast [:SPEC
                  [:narrative
                   [:as_a "product manager"]
                   [:I_want_to "add a new product to the catalog"]
                   [:so_that "I fill the catalog with interesting product"]]
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
                    [:step_sentence [:then] "I receive a 200 response"]]]])

(test/deftest get-in-ast
  (test/is (= "product manager" (first (utils/get-in-tree example-ast [:SPEC :narrative :as_a])))))

(test/deftest test-parser []
  (gherkin-parser example-scenario-multiple)
  (gherkin-parser example-scenario-unique))

(defwhen #"I create a new product with name \"([a-z 0-9]*)\" and description \"([a-z 0-9]*)\""
  [_ name desc]
  (let [id (rand-int 100000)]
    {:id id
     :name name
     :desc desc
     :qty (rand-int 50)
     :location-url (str "http://example.com/product/" id)}))

(defwhen #"I create a new product with name <product_name> and description <product_desc>"
         [_ name desc]
         (let [id (rand-int 100000)]
           {:id id
            :name name
            :desc desc
            :qty (rand-int 50)
            :location-url (str "http://example.com/product/" id)}))

(defwhen #"I create a new product with '(.*)'$"
  [_ product-map]
  (test/is (map? product-map)))

(defthen #"I receive a response with an id and a location URL"
  [{:keys [id name desc qty location-url], :as previous-return} ]
   previous-return)

(defbefore (fn [] (println "this function is executed each time before running scenarios")))
(defafter (fn [] (println "this function is executed each time after running scenarios")))

(exec-spec (slurp "resources/product-catalog.feature"))

(def step-str "(defgiven #\"this scenario in a file named (.*)\" [_ feature-file-name] (slurp feature-file-name))")
(def generic-step "the step function: (defwhen #\"I run the scenarios with '(.+)'\" [spec-str my-data] (exec-spec! spec-str)(str \"processed\" my-data)))")

(defgiven #"the step function: (.+)" [_ step-fn]
  ;;has to assoc the result, because the side effect in macros when runs normally are lost with eval
  ;;as eval runs in a fresh namespace (see http://stackoverflow.com/questions/6221716/variable-scope-eval-in-clojure)
  (let [[regex fn] (eval (read-string step-fn))]
    (prn "has executed " step-fn " extract regex " regex " and fn " fn ", new map " regexes-to-fns)
    [regex fn]))

(exec-spec "resources/spexec.feature")

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

(def examples-alone "
Examples:
  | product_name  | product_desc     |
  | iPhone 6      | telephone        |
  | iPhone 6+     | bigger telephone |
  | iPad          | tablet           |
")

(def sentence-with-parameter "
When I create a new product with name <product_name> and description <product_desc>
")

(def rules "Rule 1 [] \n when [val1 <val3(> val2] then [throw new exception]\n")


