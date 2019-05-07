(ns scenari.atm-steps
  (:require [scenari.core :refer :all]
            [clojure.test :refer [is]]))

(defrecord Card [number balance holder])
(defrecord Withdrawal [amount atm-id card-number])
(defn withdraw
  [withdrawal]
  (prn "withdraw " withdrawal)
  (conj withdrawal {:result :success}))

(defgiven
  #"the card holder \"(.*)\" has the card (.*) with a (.*) € balance"
  [_ holder card-number balance]
  (do (->Card card-number balance holder)))

(defwhen
  #"the card holder withdraw (.*) € at the ATM (.*)"
  [card amount atm-id]
  (let [withdrawal (->Withdrawal amount atm-id (:number card))]
    (withdraw withdrawal)))

(defthen
  #"he gets (.*) € in cash"
  [prev amount]
  (is (= (:amount prev) (+ 1 amount))))

(defthen
  #"the account balance is (.*) €"
  [withdrawal balance]
  (do true))

(defthen #"he gets the message \"(.*)\""
  [prev msg]
  msg)

(run-scenario "atm.feature")
