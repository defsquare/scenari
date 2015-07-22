(ns spexec.atm-steps
  (:require [spexec.core :refer :all]))

(defrecord Card [number balance holder])
(defrecord Withdrawal [amount atm-id card-number])
(defn withdraw
  [withdrawal]
  (prn "withdraw " withdrawal)
  (conj withdrawal {:result :success}))

(defgiven
  #"the card holder (.*) has the card (.*) with a (.*) € balance"
  [_ holder card-number balance]
  (do (->Card card-number balance holder)))

(defwhen
  #"the card holder withdraw (.*) € at the ATM (.*)"
  [card amount atm-id]
  (let [withdrawal (->Withdrawal amount atm-id (:number card))]
    (withdraw withdrawal)))

(defthen
  #"he gets (.*) € in cash"
  [prev-result amount]
  (do true))

(defthen
  #"the account balance is (.*) €"
  [withdrawal balance]
  (do true))