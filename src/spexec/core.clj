(ns spexec.core
  (:use [clojure.pprint])
  (:require [instaparse.core :as insta]
            [clojure.string :as string]))

(def s (insta/parser
        "S          = whitespace? epsilon;
         whitespace = #'\\s+'"))

(def gherkin-parser (insta/parser
          "SPEC               = <whitespace> (scenario <eol> <eol>)*
           scenario           = <scenario_keyword> scenario_sentence <eol> steps
           <scenario_keyword> = 'Scenario: '
           <comment>          = (comment_line whitespace?)*
           <comment_line>     = <whitespace*> <'#'> <sentence>
           steps              = (comment | <whitespace*> | step_sentence | <eol>)*
           given              = <'Given '>
           when               = <'When '>
           then               = <'Then '>
           and                = <'And '>
           <step_keyword>     = given | when | then | and
           <whitespace>       = #'\\s+'
           <space>            = ' '  | '\t'
           <eol>              = '\r' | '\n'
           scenario_sentence  = #'[a-zA-Z0-9\" ]+'
           step_sentence      = step_keyword #'[a-zA-Z0-9\" ]+'
           sentence           = #'[a-zA-Z0-9\" ]+'
           word               = #'[a-zA-Z]+'
           number             = #'[0-9]+'
           "))

(def example-scenario-unique "

Scenario: create a new product
# this is a comment
When I create a new product with name \"iphone 6\" and description \"awesome phone\"
Then I receive a response with an id 56422
And a location URL
# this a second comment
# on two lines
When I invoke a GET request on location URL
Then I receive a 200 response

")

(def example-scenario-multiple "

Scenario: create a new product
# this is a comment
When I create a new product with name \"iphone 6\" and description \"awesome phone\"
Then I receive a response with an id 56422
And a location URL
# this a second comment
# on two lines
When I invoke a GET request on location URL
Then I receive a 200 response

Scenario: get product info
#test
When I invoke a GET request on location URL
Then I receive a 200 response

")

(defprotocol STEP
  (step [this regex]))

(defn rand-from-to [from to] (+ from (rand-int to)))

(def steps (atom {}))
(def regexes (atom []));;regex can't be a key in a map, so the key are their string in the steps map, here the regexes are stored for easy retrieving

(defmacro defwhen [regex params body];;create and associate a function to the step regex
  (let [fn-name (symbol (str "when-"
                             (apply str (interpose "-" (take 2 (string/split (str regex) #" "))));;first two words of the regex
                             "-"
                             (rand-from-to 1 100000)))];;a random number
    `(do (defn ~fn-name [~@params] (~@body))
         (swap! regexes conj ~regex )
         (swap! steps assoc ~(str regex) ~fn-name))))

(def myregex #"I create a new product with name \"([a-z 0-9]*)\" and description \"([a-z 0-9]*)\""))
(def mystring "When I create a new product with name \"iphone 6\" and description \"awesome phone\"")

(defwhen #"I create a new product with name \"([a-z 0-9*)\" and description \"([a-z 0-9]*)\"" [name desc]
  (print name desc))

(defn get-steps [scenario-ast]
  (insta/transform {:sentence }))

(defn exec-scenario [scenario]
  (let [gherkin-parser scenario]
    ())
  )



(defn when-I-create-1234 [name description]
  (print "new product " name description))

(defn get-fn-matching-steps [steps fns]
  )

(pprint (gherkin-parser example-scenario ))
