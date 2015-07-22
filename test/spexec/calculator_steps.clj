(ns spexec.calculator-steps
  (:require [spexec.core :refer :all]))


(defgiven
  #"I type (.*) and (.*)"
  [_ o1 o2]
  (do "setup or assert correct tested component state"))