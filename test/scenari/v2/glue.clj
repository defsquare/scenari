(ns scenari.v2.glue
  (:require [clojure.test :refer :all]
            [scenari.v2.core :as v2]))


(v2/defwhen #"I create a new product with name \"(.*)\" and description \"(.*)\" with properties" [state arg0 arg1 arg2]
            (is (= 1 1))
            {:foo "bar"})

(v2/defthen #"I receive a response with an id 56422" [state]
            (is (= 1 1))
            state)

(v2/defthen #"a location URL" [state]
            (do "assert the result of when step")
            state)

(v2/defwhen #"I invoke a GET request on location URL" [state]
            (is (= 1 1))
            (assoc state :kix "lol"))


(v2/defgiven #"My duplicated step in other ns and feature ns" [state]
             state)

(v2/defgiven #"My duplicated step in others ns" [state]
             state)