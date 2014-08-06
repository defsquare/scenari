(ns spexec.core
  (:use [clojure.pprint])
  (:require [instaparse.core :as insta]
            [clojure.string :as string]))

(def s (insta/parser
        "S          = whitespace? epsilon;
         whitespace = #'\\s+'"))

(def gherkin-parser (insta/parser
          "S             = scenario* whitespace
           scenario      = scenario_keyword sentence eol steps
           <scenario_keyword> = 'Scenario: '
           comment       = (comment_line whitespace?)*
           comment_line = whitespace* '#' sentence
           steps         = (comment? whitespace* step_keyword sentence eol?)* epsilon
           step_keyword  = 'Given '|'When '|'Then '|'And '
           whitespace    = #'\\s+'
           space         = ' '  | '\t'
           eol           = '\r' | '\n'

           sentence      = #'[a-zA-Z0-9\" ]+'
           word          = #'[a-zA-Z]+'
           number        = #'[0-9]+'
           "))

(def example-scenario "Scenario: create a new product\n
# this is a comment\n
When I create a new product with name \"iphone 6\" and description \"awesome phone\"\n
Then I receive a response with an id 564228\n
And a location URL \n
# this a second comment
# on two lines
When I invoke a GET request on location URL\n
Then I receive a 200 response\n")

(defprotocol STEP
  (step [this regex]))

(defn rand-from-to [from to] (+ from (rand-int to)))

(def steps (atom {}))

(defmacro defwhen [regex params body]
  (let [fn-name (symbol (str "when-" (apply str (interpose "-" (take 2 (string/split (str regex) #" ")))) "-" (rand-from-to 1 100000)))]
    `(do (defn ~fn-name [~@params] (~@body))
         (swap! steps assoc ~regex ~fn-name))))

 (def myregex #"I create a new product with name \"([a-z 0-9]*)\" and description \"([a-z 0-9]*)\""))
(def mystring "When I create a new product with name \"iphone 6\" and description \"awesome phone\"")

(macroexpand-1 '(defwhen #"I create a new product with name \"([a-z 0-9]*)\" and description \"([a-z 0-9]*)\"" [name desc]
                 (print name desc)))


(defn when-I-create-1234 [name description]
  (print "new product " name description))

(defn get-fn-matching-steps [steps fns]
  )

(pprint (gherkin-parser example-scenario ))
