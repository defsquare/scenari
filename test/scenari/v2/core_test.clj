(ns scenari.v2.core-test
  (:require [clojure.test :as t :refer [is]]
            [scenari.v2.core :as v2]
            [scenari.v2.test :as sc-test]
            [scenari.v2.kaocha]
            [kaocha.repl :as krepl]
            [scenari.v2.glue]))


(v2/defwhen #"I foo" [state]
            state)

(v2/deffeature my-feature "test/scenari/v2/example.feature")


(comment
  (remove-ns 'scenari.v2.core-test)
  (meta #'scenari.v2.core-test/my-feature)
  (v2/run-features)
  (v2/run-features #'scenari.v2.core-test/my-feature)
  (sc-test/run-features #'scenari.v2.core-test/my-feature)

  (t/run-tests)

  (krepl/test-plan)
  (krepl/run-all)
  (krepl/run :scenario)

  )