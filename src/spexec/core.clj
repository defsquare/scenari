(ns spexec.core
  (:use [clojure.pprint])
  (:require [instaparse.core :as insta]
            [clojure.string :as string]
            [clojure.zip :as zip]))

(def gherkin-parser (insta/parser
          "SPEC               = <whitespace?> (scenario <eol> <eol?>)*
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

(def regexes-to-fns (atom {}))
(def regexes (atom #{}));;regex can't be a key in a map, so the key are their string in the steps map, here the regexes are stored for easy retrieving

(defn generate-fn-symbol
  "generate a function name from the regex and a random number - NOT USED ANYMORE"
  [prefix regex]
  (symbol (str "when-"
               (apply str (interpose "-" (take 2 (string/split (str regex) #" "))));;first two words of the regex
               "-"
               (rand-from-to 1 100000))))

(defn store-fns-and-regexes! [fn-symbol regex]
   (swap! regexes conj regex)
   (swap! regexes-to-fns assoc (str regex) fn-symbol))

(defmacro defgiven
  "create and associate a regex to function params and body that will match the steps string in scenarios"
  [regex params & body]
  `(let [step-fn# (fn [~@params] ~@body)]
     (store-fns-and-regexes! step-fn# ~regex)
     [~regex step-fn#]))

(defmacro defwhen
  "create and associate a regex to function params and body that will match the steps string in scenarios"
  [regex params & body]
  `(let [step-fn# (fn [~@params] ~@body)]
     (store-fns-and-regexes! step-fn# ~regex)
     [~regex step-fn#]))

(defmacro defthen
  "create and associate a regex to function params and body that will match the steps string in scenarios"
  [regex params & body]
  `(let [step-fn# (fn [~@params] ~@body)]
     (store-fns-and-regexes! step-fn# ~regex)
     [~regex step-fn#]))

;;TODO should use enlive instead of zipper for AST selection ?
(defn scenarios-ast [spec-ast]
  (-> (zip/vector-zip spec-ast) zip/down zip/rights))

(defn scenario-sentence [scenario-ast]
  (-> (zip/vector-zip scenario-ast) zip/down zip/right zip/down zip/right zip/node))

(defn steps-sentence-ast [scenario-ast]
  (-> (zip/vector-zip scenario-ast) zip/down zip/right zip/right zip/node))

(defn step-sentences [steps-ast]
  (let [v-steps-sentences (-> (zip/vector-zip steps-ast) zip/down zip/rights)]
    (map (fn [[_ [keyword] sentence]]
           sentence) v-steps-sentences)))

(defn matching-fn
  "return the tuple of fn/regex as a vector that match the step-sentence"
  [step-sentence]
  (let [matching-regexes (filter (fn [regex]
                                   (not (empty? (re-find regex step-sentence))))
                                 @regexes)]
    (if (> (count matching-regexes) 1)
      (throw (RuntimeException. (str (count matching-regexes) " matching functions were found for the following step sentence:\n " step-sentence ", please refine your regexes that match: \n" (apply str matching-regexes)))))
    [(get @regexes-to-fns (str (first matching-regexes))) (first matching-regexes)]))

(defn params-from-steps
  "return a vector of groups the regex found in the step sentence"
  [regex step-sentence]
  (rest (re-find regex step-sentence)))

(defn print-fn-skeleton [step-sentence]
  (println "(defwhen #\"" step-sentence "\" [param1 param2] (do \"something\"))"))

;;TODO include deftest with the macro define in the above and with test-ns-hook for running the test in the correct order
(defn exec-spec! [spec-str]
  ;;for each scenarios
  (doseq [scenario-ast (scenarios-ast (gherkin-parser spec-str))]
    ;;for each step sentence, find the fn which have a regex that match
    ;;execute that fn and input the return as first param of the next fn
    (println "Run:" (scenario-sentence scenario-ast))
    (loop [step-sentences (step-sentences (steps-sentence-ast scenario-ast))
           prev-ret nil]
      (if-let [step-sentence (first step-sentences)]
        (let [[fn regex] (matching-fn step-sentence)]
          (println "regex and fn " regex fn)
          (if (nil? fn)
            ;;now execute the fn with the param value extracted from the step sentence
            ;;and keep the return value to input it for the next step
            (do (print-fn-skeleton step-sentence)
                (recur (rest step-sentences) nil))
            (let [result (apply fn prev-ret (params-from-steps regex step-sentence))]
              (println "step-sentence " step-sentence)
              (println "execute fn " fn " with " prev-ret " and "  (params-from-steps regex step-sentence))
              (println "result " result)
              (recur (rest step-sentences)
                     result))))))))
