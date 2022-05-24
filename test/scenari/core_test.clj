(ns scenari.core-test
  (:require [clojure.test :as test]
            [scenari.core :refer :all]
            [scenari.utils :as utils])
  (:import java.util.UUID))

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

(gherkin-parser example-scenario-unique)
(step-parser "When I create l'a new product with name 'iphone 6' \"test\"and description \"awesome phone\"")
(step-parser "When I create l'a new product with name 'iphone 6' \"test\"and description \"awesome phone\" [123 \"test\"]")
(step-parser "When I create a new product with name <name> and description ${description}")
(step-parser "When I create a new product {:name \"iphone 6\" :description \"awesome phone\"}")

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

(test/deftest generate-step-fn-test
  (test/is (= (generate-step-fn {:sentence "When I create a new product with name \"iphone 6\""})
              "(defwhen #\"I create a new product with name \\\"([^\\\"]*)\\\"\"  [state arg0]  (do \"something\"))"))
  (test/is (= (generate-step-fn {:sentence "When I create a new product with name \"iphone 6\" and description \"awesome phone\""})
              "(defwhen #\"I create a new product with name \\\"([^\\\"]*)\\\" and description \\\"([^\\\"]*)\\\"\"  [state arg0 arg1]  (do \"something\"))"))
  (test/is (= (generate-step-fn {:sentence "When I create a new products" :tab_params [{:product_name "iPhone 6" :product_desc "telephone"}]})
              "(defwhen #\"I create a new products\"  [state arg0]  (do \"something\"))"))
  (test/is (= (generate-step-fn {:sentence "When I create a new product with name \"iPhone 6\" and others" :tab_params [{:product_name "iPhone 7" :product_desc "telephone"}]})
              "(defwhen #\"I create a new product with name \\\"([^\\\"]*)\\\" and others\"  [state arg0 arg1]  (do \"something\"))")))

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

(run-scenario "resources/product-catalog.feature")
(run-scenario "product-catalog.feature")

(def step-str "(defgiven #\"this scenario in a file named (.*)\" [_ feature-file-name] (slurp feature-file-name))")
(def generic-step "the step function: (defwhen #\"I run the scenarios with '(.+)'\" [spec-str my-data] (exec-spec! spec-str)(str \"processed\" my-data)))")

(defgiven #"the step function: (.+)" [_ step-fn]
  ;;has to assoc the result, because the side effect in macros when runs normally are lost with eval
  ;;as eval runs in a fresh namespace (see http://stackoverflow.com/questions/6221716/variable-scope-eval-in-clojure)
  (let [[regex fn] (eval (read-string step-fn))]
    (prn "has executed " step-fn " extract regex " regex " and fn " fn ", new map " regexes-to-fns)
    [regex fn]))

(run-scenario "resources/scenari.feature")

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

(examples (examples-ast (gherkin-parser scenario-with-examples)))
(examples-ast (gherkin-parser scenario-with-examples))
(utils/get-whole-in (gherkin-parser scenario-with-examples) [:SPEC :scenario :examples])
(utils/get-in-tree (gherkin-parser scenario-with-examples) [:SPEC :scenario :examples])
(get-in (gherkin-parser scenario-with-examples) [1 3])
(gherkin-parser scenario-with-examples)

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

(def scenario-with-tab-param "
Scenario: create a new product
# this is a comment
When I create a new products
  | product_name  | product_desc     |
  | iPhone 6      | telephone        |
  | iPhone 6+     | bigger telephone |
  | iPad          | tablet           |
Then I receive a response with an id")

(test/deftest scenario-with-tab-params-test
  (test/is (= (gherkin-parser scenario-with-tab-param)
              [:SPEC
               [:scenario
                [:scenario_sentence " create a new product"]
                [:steps
                 [:step_sentence
                  [:when]
                  "I create a new products"
                  [:tab_params
                   [:header " product_name  " " product_desc     "]
                   [:row " iPhone 6      " " telephone        "]
                   [:row " iPhone 6+     " " bigger telephone "]
                   [:row " iPad          " " tablet           "]]]
                 [:step_sentence [:then] "I receive a response with an id"]]]])))

(test/deftest params-from-steps-test
  (test/is (=
             (params-from-steps #"When I create a new products \"(.*)\" \"(.*)\"" {:sentence   "When I create a new products \"toto\" \"123-456\""})
             ["toto" "123-456"]))
  (test/is (=
             (params-from-steps #"When I create a new products \"(.*)\" \"(.*)\"" {:sentence   "When I create a new products \"toto\" \"{:foo :bar}\""})
             ["toto" {:foo :bar}]))
  (test/is (=
             (params-from-steps #"When I create a new products \"(.*)\" \"(.*)\"" {:sentence   "When I create a new products \"toto\" \"[:foo :bar]\""})
             ["toto" [:foo :bar]]))
  (test/is (=
             (params-from-steps #"When I create a new products \"(.*)\" \"(.*)\"" {:sentence   "When I create a new products \"toto\" \"(:foo :bar)\""})
             ["toto" (list :foo :bar)]))
  (test/is (=
             (params-from-steps #"When I create a new products \"(.*)\" \"(.*)\"" {:sentence   "When I create a new products \"toto\" \"#{:foo :bar}\""})
             ["toto" #{:foo :bar}]))
  (test/is (=
             (params-from-steps #"When I create a new products \"(.*)\" \"(.*)\"" {:sentence   "When I create a new products \"toto\" \"coucou\""
                                                                                   :tab_params [{:product_name "iPhone 6" :product_desc "telephone"}]})
             ["toto" "coucou" [{:product_name "iPhone 6", :product_desc "telephone"}]]))
  (test/is (=
             (params-from-steps #"When I create a new products \"(.*)\" \"(.*)\"" {:sentence "When I create a new products \"toto\" \"coucou\""})
             ["toto" "coucou"]))
  (test/is (=
             (params-from-steps #"When I create a new products" {:sentence "When I create a new products"})
             []))
  (test/is (=
             (params-from-steps #"When I create a new products" {:sentence "When I create a new products" :tab_params [{:product_name "iPhone 6" :product_desc "telephone"}]})
             [[{:product_name "iPhone 6", :product_desc "telephone"}]])))

(def scenario-with-tab-params-containing-unicodes "
Scenario: create a new product
# this is a comment
When I create a new products
  | product_name  | product_desc              |
  | iPhone 6      | téléphone                 |
  | iPhone 6+     | bigger téléphone+-,'23454 |
  | iPad          | tablet'                   |
Then I receive a response with an id")

(test/deftest scenario-parsing
  (test/testing "tab params handle values containing unicode characters"
    (test/is (= (gherkin-parser scenario-with-tab-params-containing-unicodes)
                [:SPEC
                 [:scenario
                  [:scenario_sentence " create a new product"]
                  [:steps
                   [:step_sentence
                    [:when]
                    "I create a new products"
                    [:tab_params
                     [:header " product_name  " " product_desc              "]
                     [:row " iPhone 6      " " téléphone                 "]
                     [:row " iPhone 6+     " " bigger téléphone+-,'23454 "]
                     [:row " iPad          " " tablet'                   "]]]
                   [:step_sentence [:then] "I receive a response with an id"]]]]
                ))))


(test/deftest step-sentences-test
  (test/testing "with tabs params"
    (test/is (= (step-sentences [:steps
                                 [:step_sentence
                                  [:when]
                                  "I create a new products"
                                  [:tab_params
                                   [:header " product_name  " " product_desc     "]
                                   [:row " iPhone 6      " " telephone        "]
                                   [:row " iPhone 6+     " " bigger telephone "]
                                   [:row " iPad          " " tablet           "]]]
                                 [:step_sentence
                                  [:then]
                                  "coucou"]])
                [{:sentence   "When I create a new products"
                  :tab_params [{:product_desc "telephone" :product_name "iPhone 6"}
                               {:product_desc "bigger telephone" :product_name "iPhone 6+"}
                               {:product_desc "tablet" :product_name "iPad"}]}
                 {:sentence "Then coucou"}]))))

(defwhen #"I create a new products" [state arg0] (test/testing "assert tab params"
                                                   (test/is (= (count arg0) 3))
                                                   (test/is (= (:product_name (first arg0)) "iPhone 6"))))
(defthen #"I receive a response with an id"  [state ]  (do "assert the result of when step"))

(run-scenario scenario-with-tab-param)