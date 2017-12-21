(ns scenari.utilisateurs-steps
  (:require [scenari.core :as spec :refer [Given When Then exec-specs]]))

(Given #"l'utilisateur (.*) n'existe pas"
       (fn [_ mail1]
         {:mail mail1}))

(When #"je crée l'utilisateur <mail1>"
      (fn [_ mail1]
        (str "je cree " mail1)))

(Then #"l'utilisateur <mail1> existe dans le repository des utilisateurs"
      (fn [_ mail1]
        (str "existe " mail1)))
