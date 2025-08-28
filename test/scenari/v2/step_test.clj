(ns scenari.v2.step-test
  (:require [clojure.test :as t]
            [scenari.v2.step :refer [generate-step-fn]]))

(t/deftest generate-step-fn-test
           (t/is (= (generate-step-fn {:sentence "When I create a new product with name \"iphone 6\""})
                    "(defwhen \"I create a new product with name {string}\"  [state arg0]  (do \"something\"))"))
           (t/is (= (generate-step-fn {:sentence "When I create a new product with name \"iphone 6\" and description \"awesome phone\""})
                    "(defwhen \"I create a new product with name {string} and description {string}\"  [state arg0 arg1]  (do \"something\"))"))
           (t/is (= (generate-step-fn {:sentence "When I create a new products" :tab_params [{:product_name "iPhone 6" :product_desc "telephone"}]})
                    "(defwhen \"I create a new products\"  [state arg0]  (do \"something\"))"))
           (t/is (= (generate-step-fn {:sentence "When I create a new product with name \"iPhone 6\" and others" :tab_params [{:product_name "iPhone 7" :product_desc "telephone"}]})
                    "(defwhen \"I create a new product with name {string} and others\"  [state arg0 arg1]  (do \"something\"))"))
           (t/is (= (generate-step-fn {:sentence "When I create a new product with id 1234" :tab_params [{:product_name "iPhone 7" :product_desc "telephone"}]})
                    "(defwhen \"I create a new product with id {number}\"  [state arg0]  (do \"something\"))")))

