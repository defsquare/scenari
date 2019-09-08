(ns scenari.v2.core-test
  (:require [clojure.test :as t :refer [is]]
            [scenari.v2.core :as v2]))

(v2/deffeature my-feature
            "Feature: foo bar kix
Scenario: create a new product
# this is a comment
When I create a new product with name \"iphone 6\" and description \"awesome phone\"
Then I receive a response with an id 56422
Then a location URL
# this a second comment
# on two lines
When I invoke a GET request on location URL

Scenario: other
# this is a comment
When I foo
"

            (v2/defwhen #"I create a new product with name \"(.*)\" and description \"(.*)\"" [state arg0 arg1]
                     (is (= 1 1))
                     {:foo "bar"})

            (v2/defthen #"I receive a response with an id 56422" [state]
                     (do "assert the result of when step")
                     state)

            (v2/defthen #"a location URL" [state]
                     (do "assert the result of when step")
                     state)

            (v2/defwhen #"I invoke a GET request on location URL" [state]
                     (is (= 1 1))
                     (assoc state :kix "lol"))

            (v2/defwhen #"I foo" [state]
                     (do "something")
                     state))

(comment

  (remove-ns 'scenari.v2.core-test)
  (v2/run-features #'scenari.v2.core-test/my-feature)
  (v2/run-features)
  )