;The MIT License (MIT)
;
;Copyright (c) 2014 Jérémie Grodziski
;
;Permission is hereby granted, free of charge, to any person obtaining a copy
;of this software and associated documentation files (the "Software"), to deal
;in the Software without restriction, including without limitation the rights
;to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
;copies of the Software, and to permit persons to whom the Software is
;furnished to do so, subject to the following conditions:
;
;The above copyright notice and this permission notice shall be included in all
;copies or substantial portions of the Software.
;
;THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
;IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
;FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
;AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
;LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
;OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
;SOFTWARE.
;

(ns spexec.core
  (:require [instaparse.core :as insta]
            [taoensso.timbre :as timbre]
            [clojure.string :as string]
            [clojure.zip :as zip]
            [clojure.pprint :refer :all]))
(timbre/refer-timbre)

(def gherkin-parser (insta/parser
          "SPEC               = <whitespace?> narrative? <whitespace?> (scenario <eol> <eol?>)*
           narrative          = <'Narrative:'> <eol>? (as_a I_want_to in_order_to | as_a I_want_to so_that )?
           in_order_to        = <'In order to '> #'.*' <eol>
           as_a               = <'As a '> #'.*' <eol>
           I_want_to          = <'I want to '> #'.*' <eol>
           so_that            = <'So that '> #'.*' <eol>
           NarrativeStartingWord = ('In order to' | 'As a' | 'I want to' | 'So that')
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
           scenario_sentence  = #'.*'
           step_sentence      = step_keyword #'.*'
           sentence           = #'[a-zA-Z0-9\" ]+'
           word               = #'[a-zA-Z]+'
           number             = #'[0-9]+'
           "))

(def keywords-str {:given "Given "
                   :when "When "
                   :then "Then "
                   :and "And "})

;;TODO make the steps macro that generate the fn implement the protocol
(defprotocol STEP
  (step [this regex]))

(defn rand-from-to [from to] (+ from (rand-int to)))

(def regexes-to-fns (atom {}));;store the regex as a string, as keys can't be regex in a map and also because same regex expression are different object in Java...:(
;;(def regexes (atom #{}));;regex can't be a key in a map, so the key are their string in the steps map, here the regexes are stored for easy retrieving

(defn spexec-pprint-dispatch [str]
  (if (reduce (fn [prev curr] (or prev (.startsWith str curr)))
              false
              (vals keywords-str))
    (print "  " str)
    (if (.startsWith str "=>")
      (print "    " str)
      (print str))))

(defn generate-fn-symbol
  "generate a function name from the regex and a random number - NOT USED ANYMORE"
  [prefix regex]
  (symbol (str "when-"
               (apply str (interpose "-" (take 2 (string/split (str regex) #" "))));;first two words of the regex
               "-"
               (rand-from-to 1 100000))))

(defn store-fns-and-regexes! [regex fn]
   ;;(swap! regexes conj regex)
   (swap! regexes-to-fns assoc (str regex) fn)
   [regex fn])

(defn step-given [regex fn]
  "create and associate a regex to a function that will match the steps string in scenarios"
  (store-fns-and-regexes! regex fn))

(defn step-when [regex fn]
  "create and associate a regex to a function that will match the steps string in scenarios"
  (store-fns-and-regexes! regex fn))

(defn step-then [regex fn]
  "create and associate a regex to a function that will match the steps string in scenarios"
  (store-fns-and-regexes! regex fn))

(def before (atom nil))
(def after  (atom nil))

(defn defbefore
  "Define a function that will get executed BEFORE every steps when exec-spec will be invoked "
  [f]
  (reset! before f))

(defn defafter
  "Define a function that will get executed AFTER every steps when exec-spec will be invoked (or in case of failure)"
  [f]
  (reset! after f))

(defmacro defgiven
  "create and associate a regex to function params and body that will match the steps string in scenarios"
  [regex params & body]
  `(let [step-fn# (fn [~@params] ~@body)]
     (step-given ~regex step-fn#)))

(defmacro defwhen
  "create and associate a regex to function params and body that will match the steps string in scenarios"
  [regex params & body]
  `(let [step-fn# (fn [~@params] ~@body)]
     (step-when ~regex step-fn#)))

(defmacro defthen
  "create and associate a regex to function params and body that will match the steps string in scenarios"
  [regex params & body]
  `(let [step-fn# (fn [~@params] ~@body)]
     (step-then ~regex step-fn#)))

;;TODO should use enlive instead of zipper for AST selection ?
(defn- elements-ast [spec-ast]
  (-> (zip/vector-zip spec-ast) zip/down zip/rights))

(defn scenarios-ast [spec-ast]
  (let [elements (elements-ast spec-ast)]
    (if (= (ffirst elements) :narrative)
      (rest elements)
      elements)))

(defn narrative-ast [spec-ast]
  (let [elements (elements-ast spec-ast)]
    (if (= (ffirst elements) :narrative)
      (first elements)
      nil)))

(defn narrative-str [narrative-ast]
  (apply str(map (fn [e]
                   (if (instance? java.lang.String e)
                     e
                     (case e
                       :narrative "Narrative: "
                       :as_a "As a "
                       :in_order_to " in order to "
                       :I_want_to " I want to "
                       :so_that " so that "))) (flatten narrative-ast))))

(defn scenario-sentence [scenario-ast]
  (-> (zip/vector-zip scenario-ast) zip/down zip/right zip/down zip/right zip/node))

(defn steps-sentence-ast [scenario-ast]
  (-> (zip/vector-zip scenario-ast) zip/down zip/right zip/right zip/node))

(defn step-sentences [steps-ast]
  (let [v-steps-sentences (-> (zip/vector-zip steps-ast) zip/down zip/rights)]
    (map (fn [[_ [keyword] sentence]]
           (str (keyword keywords-str) sentence)) v-steps-sentences)))

(defn matching-fn
  "return the tuple of fn/regex as a vector that match the step-sentence"
  [step-sentence]
  ;;first map each regex string into a regex object then filter the regex that match
  (let [matching-regexes
        (filter (fn [regex]
                  (not (empty? (re-find regex step-sentence))))
                (map re-pattern (keys @regexes-to-fns)))]
    (if (> (count matching-regexes) 1)
      (throw (RuntimeException. (str (count matching-regexes) " matching functions were found for the following step sentence:\n " step-sentence ", please refine your regexes that match: \n" (apply str matching-regexes)))))
    [(get @regexes-to-fns (str (first matching-regexes))) (first matching-regexes)]))

(defnp params-from-steps
  "return a vector of groups the regex found in the step sentence, nil otherwise"
  [regex step-sentence]
  (let [find-result (re-find regex step-sentence)]
    (if (coll? find-result)
      (rest find-result)
      nil)))

(defn print-fn-skeleton [step-sentence]
  (println (timbre/color-str :yellow "No function found we suggest adding: (defwhen #\"" step-sentence "\" [param1 param2] (do \"something\"))")))

(defn- exec-scenario [scenario-ast]
  ;;for each step sentence, find the fn which have a regex that match
  ;;execute that fn and input the return as first param of the next fn
  (println  "Run Scenario:" (scenario-sentence scenario-ast))
  (loop [step-sentences (step-sentences (steps-sentence-ast scenario-ast))
         prev-ret       nil
         scenario-acc [:scenario (scenario-sentence scenario-ast)]]
    (if-let [step-sentence (first step-sentences)]
      (let [[fn regex] (matching-fn step-sentence)]
        (trace "regex and fn " regex fn)
        (if (nil? fn)
          ;;now execute the fn with the param value extracted from the step sentence
          ;;and keep the return value to input it for the next step
          (do (print-fn-skeleton step-sentence)
              (recur (rest step-sentences)
                     nil
                     (conj scenario-acc [step-sentence nil])))
          ;;fn found with a regex that match the sentence
          (let [result (apply fn prev-ret (params-from-steps regex step-sentence))]
            (with-pprint-dispatch spexec-pprint-dispatch (pprint step-sentence))
            (with-pprint-dispatch spexec-pprint-dispatch (pprint (str "=> " result)))
            (trace "executed fn " fn " with " prev-ret " and "  (params-from-steps regex step-sentence) ", result => " result)
            (recur (rest step-sentences)
                   result
                   (conj scenario-acc [step-sentence result])))))
      scenario-acc))
  )

;;TODO include deftest with the macro define in the above and with test-ns-hook for running the test in the correct order
(defmulti exec-spec
  "Read the spec and execute each step with the code setup by the defgiven, defwhen and defthen macro"
  ;; the dispatch fn do that on the type of the parameter but
  ;; if it's string, it first check if it's the name of the file
  ;; otherwise considers it's the spec itself
  (fn [spec]
    (println (type spec))
    (if (and (= (type spec) java.lang.String)
                     (.exists (java.io.File. spec)))
              java.io.File
              (type spec)))
  :default java.io.File)

(defmethod exec-spec
  java.io.File
  [spec-file]
  (exec-spec (slurp spec-file)))

(defmethod exec-spec
  java.lang.String
  [spec-str]
  ;;for each scenarios
  (do (@before)
      (let [spec-parse-tree (gherkin-parser spec-str)]
        (if (insta/failure? spec-parse-tree)
          (do (println "The supplied spec contains a parse error, please fix it, if you tried to supply a filename it was not found, please verify the relative path (btw that test get executed in the following dir:" (java.lang.System/getProperty "user.dir") ")")
              (insta/get-failure spec-parse-tree))
          (loop [scenarios (scenarios-ast spec-parse-tree)
                 spec-acc [(do (let [narrative (narrative-str (narrative-ast spec-parse-tree))]
                                 (println narrative)
                                 narrative))]]
            (if-let [scenario-ast (first scenarios)]
              (let [exec-result (exec-scenario scenario-ast)]
                (recur (rest scenarios) (conj spec-acc exec-result)))
              (do (print "\n")
                  spec-acc)))))
      (@after)))
