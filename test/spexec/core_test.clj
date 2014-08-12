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

(def example-scenario-multiple "Scenario: create a new product
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

(gherkin-parser example-scenario-multiple)

(defwhen #"I create a new product with name \"([a-z 0-9]*)\" and description \"([a-z 0-9]*)\"" [_ name desc]
  (println "executing my product creation function with params " name desc)
  {:name name :desc desc :qty (rand-int 50)})

(defthen #"I receive a response with an id ([0-9]+)"
  [_ id]
  (println (str "executing the assertion that the product has been created with the id " id))
  id)

(exec-spec! (slurp "resources/product-catalog.feature"))

(def step-str "(defgiven #\"this scenario in a file named (.*)\" [_ feature-file-name] (slurp feature-file-name))")
(def generic-step "the step function: (defwhen #\"I run the scenarios with '(.+)'\" [spec-str my-data] (exec-spec! spec-str)(str \"processed\" my-data)))")

(reset! regexes #{})
(reset! regexes-to-fns {})

(defgiven #"the step function: (.+)" [_ step-fn]
  ;;has to assoc the result, because the side effect in macros when runs normally are lost with eval
  ;;as eval runs in a fresh namespace (see http://stackoverflow.com/questions/6221716/variable-scope-eval-in-clojure)
  (let [[regex fn] (eval (read-string step-fn))]
    (println "has executed " step-fn " extract regex " regex " and fn " fn)
    ;;(swap! regexes conj regex)
    ;;(swap! regexes-to-fns assoc (str regex) fn)
    [regex fn]))

(exec-spec! (slurp "resources/spexec.feature"))
