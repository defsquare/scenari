(ns scenari.v2.core-test
  (:require [clojure.test :as t :refer [is]]
            [scenari.v2.core :as v2]
            [scenari.v2.test :as sc-test]
            [clojure.string :as string]))

(v2/defwhen #"I create a new product with name \"(.*)\" and description \"(.*)\" with properties" [state arg0 arg1 arg2]
            (is (= 1 2))
            {:foo "bar"})

(v2/defthen #"I receive a response with an id 56422" [state]
            (is (= 1 1))
            state)

(v2/defthen #"a location URL" [state]
            (do "assert the result of when step")
            state)

(v2/defwhen #"I invoke a GET request on location URL" [state]
            (is (= 1 1))
            (println "coucou")
            (assoc state :kix "lol"))

(v2/defwhen #"I foo" [state]
            state)

(v2/deffeature my-feature "test/scenari/v2/example.feature")


(comment
  (meta #'I-create-a-new-product-with-name-param-and-description-param-with-properties)
  (remove-ns 'scenari.v2.core-test)

  (eval '((fn [] "coucou")))
  (map (fn [f] (do (f))) [(fn [] "coucou")])

  (apply do (fn [] "coucou"))
  (macroexpand '(v2/deffeature my-feature
                                 "test/scenari/v2/example.feature"

                                 (v2/defwhen #"I create a new product with name \"(.*)\" and description \"(.*)\" with properties" [state arg0 arg1 arg2]
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
                                             state)))
  (meta #'lol)
  (meta #'my-feature)
  (meta #'I-create-a-new-product-with-name-param-and-description-param-with-properties)
  (meta #'a-location-URL)



  (my-feature)
  (sc-test/run-features #'scenari.v2.core-test/my-feature)
  (v2/run-features #'scenari.v2.core-test/my-feature)
  (v2/run-features)

  (cons {:foo "bar"} ["iphone 6" "awesome phone" [{:size "6", :weight "2"}]])
  (concat {:foo "bar"} ["iphone 6" "awesome phone" [{:size "6", :weight "2"}]])
  )