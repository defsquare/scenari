(ns scenari.v2.other-glue.glue
  (:require [clojure.test :refer :all]
            [scenari.v2.core :as v2]))

(v2/defgiven #"My duplicated step in others ns" [state]
             state)