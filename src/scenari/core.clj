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

(ns scenari.core
  (:require [instaparse.core :as insta]
            [taoensso.timbre :as timbre]
            [clojure.string :as string]
            [clojure.zip :as zip]
            [clojure.edn :only read-string]
            [clojure.pprint :refer :all]
            [clojure.java.io :only [file as-file] :as io]
            [scenari.utils :as utils :refer [get-in-tree]])
   (:import org.apache.commons.io.FileUtils
            org.apache.commons.io.filefilter.RegexFileFilter))
(timbre/refer-timbre)

(def kw-translations-data {:fr {:given    "Etant donné que " :when "Quand " :and "Et "
                                :then     "Alors " :scenario "Scénario :"
                                :examples "Exemples :"
                                :narrative "Narrative: "
                                :as_a "En tant que "
                                :in_order_to " afin de "
                                :I_want_to " Je veux "
                                :so_that " afin de "}
                           :en {:given    "Given " :when "When " :and "And "
                                :then     "Then " :scenario "Scenario:"
                                :examples "Examples:"
                                :narrative "Narrative: "
                                :as_a "As a "
                                :in_order_to " in order to "
                                :I_want_to " I want to "
                                :so_that " so that "}})

(defn- kw-translations
  "return a string consisting of appending the keyword separated by | for inclusion in gherkin grammar"
  ([kw data]
   (apply str
          (interpose "|"
                     (map (comp #(str "'" % "'")
                                val
                                first
                                (partial filter (fn [e] (= (key e) kw))))
                          (vals data)))))
  ([kw]
   (kw-translations kw kw-translations-data)))

(def gherkin-parser (insta/parser
                      (str "SPEC = <whitespace?> <comment?> narrative? <whitespace?> <comment?> (scenario <eol?> <eol?>)*
           narrative          = <'Narrative:'|'Feature:'> <sentence>? <eol>? (as_a I_want_to in_order_to |
                                                                              as_a I_want_to so_that | in_order_to as_a I_want_to |
                                                                              as_a in_order_to I_want_to)?
           in_order_to        = <whitespace>? <'In order to '> #'.*' <eol>
           as_a               = <whitespace>? <'As a '> #'.*' <eol>
           I_want_to          = <whitespace>? <'I want to '> #'.*' <eol>
           so_that            = <whitespace>? <'So that '> #'.*' <eol>
           <scenario_keyword> = " (kw-translations :scenario) "
           scenario           = <scenario_keyword> scenario_sentence <eol> steps examples?
           <comment>          = (comment_line whitespace?)*
           <comment_line>     = <whitespace*> <'#'> <sentence>
           steps              = (comment | <whitespace*> | step_sentence | <eol>)*
           given              = <" (kw-translations :given) ">
           when               = <" (kw-translations :when) ">
           then               = <" (kw-translations :then) ">
           and                = <" (kw-translations :and) ">
           <step_keywords>    = given | when | then | and
           <whitespace>       = #'\\s+'
           <space>            = ' '  | '\t'
           <eol>              = '\r' | '\n'
           scenario_sentence  = #'.*'
           step_sentence      = step_keywords #'.*'
           sentence           = #'[a-zA-Z0-9\"./\\_\\-\\':<>é@ ]+'
           examples           = <whitespace?> examples-keywords <eol> header row* <eol?>
           <examples-keywords>= <" (kw-translations :examples) ">
           header             = <whitespace?> (<'|'> column_name)+ <'|'> <eol>
           <column_name>      = <whitespace?> #'[a-zA-Z0-9_\\- ]+' <whitespace?>
           row                = <whitespace?> (<'|'> <whitespace?> value )+ <whitespace?> <'|'> <eol>
           <value>            = #'[a-zA-Z0-9+@. ]*'
           word               = #'[a-zA-Z]+'
           number             = #'[0-9]+'
           ")))

(def rules-parser (insta/parser
                   "<RULES>      = rule* <eol>*
                    rule         = <'Rule '> rule_name rule_params when_clause then_clause <eol?>
                    rule_name    = #'[a-zA-Z0-9]+' <whitespace> <eol?>
                    rule_params  = <'['> rule_param* <']'> <whitespace> <eol?>
                    rule_param   = #'[a-zA-Z0-9]+' <whitespace>
                    when_clause  = <('when'|'When')> <whitespace> condition <whitespace> <eol?>
                    condition    = <'['> (#'[a-zA-Z0-9 .]*' | data_holder)* <']'>
                    data_holder  = <'<'> #'[a-zA-Z0-9_]+' <'>'>
                    then_clause  = <('then'|'Then')> <whitespace> action <whitespace> <eol?>
                    action       = <'['> (#'[a-zA-Z0-9 .]*' | data_holder)* <']'>
                    whitespace   = #'\\s+'
                    eol          = '\r' | '\n'"))

(def examples-parser (insta/parser
                      "<EXAMPLES>    = <whitespace?> <'Examples:'> <eol> header row* <eol?>
                       header        = <whitespace?> (<'|'> column_name)+ <'|'> <eol>
                       <column_name> = <whitespace?> #'[a-zA-Z0-9_\\- ]+' <whitespace?>
                       row           = <whitespace?> (<'|'> <whitespace?> value )+ <whitespace?> <'|'> <eol>
                       <value>       = #'[a-zA-Z0-9+ ]*'
                       whitespace    = #'\\s+'
                       eol           = '\r' | '\n'"))

(def sentence-parser (insta/parser
                       (str "SENTENCE         = <whitespace?> step_keyword (words | data_group | parameter)* <eol>?
                             given            = <" (kw-translations :given) ">
                             when             = <" (kw-translations :when) ">
                             then             = <" (kw-translations :then) ">
                             and              = <" (kw-translations :and) ">
                             words            = #'[a-zA-Z0-9./\\_\\-\\'èéàûù ]+'
                             <parameter_name> = #'[a-zA-Z0-9\"./\\_\\- ]+'
                             parameter        = <'<'> parameter_name <'>'>
                             <delimeter>      = <'\"'>
                             <data_group>     = <delimeter> data <delimeter> | map
                             map              = #'\\{[a-zA-Z0-9\\-:,./\\\" ]+\\}'
                             data             = #'[a-zA-Z0-9\\-:,./ ]+'
                             <step_keyword>   = given | when | then | and
                             <whitespace>     = #'\\s+'
                             eol              = '\r' | '\n'
")))

(def keywords-str {:given "Given "
                   :when "When "
                   :then "Then "
                   :and "And "})

(defn spexec-pprint-dispatch [str]
  (if (reduce (fn [prev curr] (or prev (.startsWith str curr)))
              false
              (vals keywords-str))
    (print "  " str)
    (if (.startsWith str "=>")
      (print "    " str)
      (print str))))

(defn- extract-data-as-args [sentence-elements]
  (let [data-count (count (filter (fn [c] (= (first c) :data)) sentence-elements))
        data-args (clojure.string/join "_" (for [i (range data-count)] (str "arg" i)))]
    (str "[" data-args "]")))

(defn generate-step-fn
  "return a string representing a spexec macro call corresponding to the sentence step"
  [step-sentence]
  (let [sentence-ast (sentence-parser step-sentence)
        sentence-elements (rest sentence-ast)
        step-type (ffirst sentence-elements)]
    (if (insta/failure? sentence-ast)
      (do (prn (insta/get-failure sentence-ast
                                  )) (throw (ex-info (:reason (insta/get-failure sentence-ast)) {:parsed-text step-sentence}))))
    (str (case step-type
           :given "(defgiven #\""
           :when  "(defwhen #\""
           :then  "(defthen #\""
           "(defwhen #\"")
         (apply str (map (fn [c]
                           (let [what? (first c)]
                             (case what?
                               :words (second c)
                               :data "'(.*)'"
                               "test"))) (rest sentence-elements)))
         "\"  "
         (extract-data-as-args sentence-elements)
         (case step-type
           :given "  (do \"setup or assert correct tested component state\"))"
           :when  "  (do \"something\"))"
           :then  "  (do \"assert the result of when step\"))"
           "  (do \"something\"))"))))

(defn remove-non-word-character [regex-str]
     ;;transform space to -, remove quote and ", regex group to _
  (clojure.string/replace regex-str
                          #"([\W])"
                          (fn [vec] (if (= (vec 0) " ") "-" ""))))

(defn print-fn-skeleton [step-sentence]
  (println (timbre/color-str :yellow "No function found for step: " step-sentence "\nYou may define a corresponding step function with: \n   " (generate-step-fn step-sentence))))

(def regexes-to-fns (atom {}));;store the regex as a string, as keys can't be regex in a map and also because same regex expression are different object in Java...:(

(defn reset-steps! [] (reset! regexes-to-fns {}))

(defn store-fns-and-regexes! [regex fn]
   (swap! regexes-to-fns assoc (str regex) fn)
   [regex fn])

(defn bind-symbol-from [regex fn]
  (intern *ns* (symbol (remove-non-word-character (str regex))) fn))

(defn Given [regex fn]
  "create and associate a regex to a function that will match the steps string in scenarios"
  (store-fns-and-regexes! regex (bind-symbol-from regex fn)))

(defn When [regex fn]
  "create and associate a regex to a function that will match the steps string in scenarios"
  (store-fns-and-regexes! regex (bind-symbol-from regex fn)))

(defn Then [regex fn]
  "create and associate a regex to a function that will match the steps string in scenarios"
  (store-fns-and-regexes! regex (bind-symbol-from regex fn)))

(def before (atom []))
(def after  (atom []))

(defn defbefore
  "Define a function that will get executed BEFORE every steps when exec-spec will be invoked "
  [f]
  (swap! before conj f))

(defn defafter
  "Define a function that will get executed AFTER every steps when exec-spec will be invoked (or in case of failure)"
  [f]
  (swap! after conj f))

(defmacro defgiven
  "create and associate a regex to function params and body that will match the steps string in scenarios"
  [regex params & body]
  `(let [step-fn# (fn [~@params] ~@body)]
     (Given ~regex step-fn#)))

(defmacro defwhen
  "create and associate a regex to function params and body that will match the steps string in scenarios"
  [regex params & body]
  `(let [step-fn# (fn [~@params] ~@body)]
     (When ~regex step-fn#)))

(defmacro defthen
  "create and associate a regex to function params and body that will match the steps string in scenarios"
  [regex params & body]
  `(let [step-fn# (fn [~@params] ~@body)]
     (Then ~regex step-fn#)))

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

(defn narrative-str
  "transform a narrative tree to a string"
  [narrative-ast]
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
  (first (get-in-tree scenario-ast [:scenario :scenario_sentence])))

(defn steps-sentence-ast [scenario-ast]
  (-> (zip/vector-zip scenario-ast) zip/down zip/right zip/right zip/node))

(defn step-sentences [steps-ast]
  (let [v-steps-sentences (-> (zip/vector-zip steps-ast) zip/down zip/rights)]
    (map (fn [[_ [keyword] sentence]]
           (str (keyword (:en kw-translations-data)) sentence)) v-steps-sentences)))

(defn examples-ast [scenario-ast]
  (first (utils/get-whole-in scenario-ast [:scenario :examples])))

(defn examples
  "return a vector of map with header name as key and row value"
  [examples-ast]
  (let [headers (map string/trim (utils/get-in-tree examples-ast [:examples :header]))
        rows (map (fn [row] (map string/trim (rest row))) (utils/get-whole-in examples-ast [:examples :row]))]
    (map (partial zipmap headers) rows)))

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
    (if (= (count matching-regexes) 0)
      (do (print-fn-skeleton step-sentence)
          nil))
    [(get @regexes-to-fns (str (first matching-regexes))) (first matching-regexes)]))

(defn params-from-steps
  "return a vector of data as string as found in groups the regex found in the step sentence,
  if the data is a clojure data structure then it will be evaluated otherwise returned as a string,
  nil if no groups are found"
  [regex step-sentence]
  (let [find-result (re-find regex step-sentence)]
    (if (coll? find-result)
      (map (fn [data] (let [data-evaluated (clojure.edn/read-string data)]
                       (if (coll? data-evaluated) data-evaluated data)))
           (rest find-result))
      nil)))

(defn- scenario-with-examples? [scenario-ast]
  (not (nil? (utils/get-in-tree scenario-ast [:scenario :examples]))))

(defn data-from-example
  "return a vector of data as string as found in example row"
  [example]
  (vals example))

(defn- execute-fn-for-step [step-sentence fn prev-return params]
  ;;(trace "regex and fn " regex fn)
  (if (not (nil? fn))
    ;; fn found with a regex that match the sentence
    ;; now execute the fn with the param value extracted from the step sentence
    ;; and keep the return value to input it for the next step
    ;; so if you want to accumulate the results, just provide a coll and conj on it
    (let [result
          (try
            (apply fn prev-return params)
            (catch java.lang.Exception e {:failure true :message (.getMessage e)}))
          ]
      (with-pprint-dispatch spexec-pprint-dispatch (pprint step-sentence))
      (with-pprint-dispatch spexec-pprint-dispatch (pprint (str "=> " result)))
      (trace "executed fn " fn " with " prev-return " and " params ", result => " result)
      result)
    (do (print-fn-skeleton step-sentence)
        nil)))

(defn replace-params-with-data [step-sentence data]
  )

(defn exec-scenario-with-examples
  "run the scenario for each example rows, then
   for each step sentence find the fn which have a regex that match
   get the parameters value from the example and replace the parameter in the sentence
   then execute that fn with the parameters and input the return as first param of the next fn"
  [scenario-ast]
  (let [step-sentences (step-sentences (steps-sentence-ast scenario-ast))
        examples (examples (examples-ast scenario-ast))]
    (println  "Run Scenario:" (scenario-sentence scenario-ast) "with" (count examples) " examples")
    (loop [examples examples]
      (println " ")
      (if-let [example (first examples)]
        (do (loop [step-sentences step-sentences
                   prev-return nil
                   scenario-acc [:scenario (scenario-sentence scenario-ast)]]
              (if-let [step-sentence (first step-sentences)]
                (let [[fn regex] (matching-fn step-sentence)
                      data (data-from-example example)
                      result (execute-fn-for-step step-sentence fn prev-return data)]
                  (recur (rest step-sentences)
                         result
                         (conj scenario-acc [step-sentence result])))))
            (recur (rest examples)))))))

(defn exec-scenario
  "for each step sentence, find the fn which have a regex that match
   execute that fn and input the return as first param of the next fn"
  [scenario-ast]
  (println  "Run Scenario:" (scenario-sentence scenario-ast))
  (loop [step-sentences (step-sentences (steps-sentence-ast scenario-ast))
         prev-return nil
         scenario-acc [:scenario (scenario-sentence scenario-ast)]]
    (if-let [step-sentence (first step-sentences)]
      (let [[fn regex] (matching-fn step-sentence)]
        (if (and (not (nil? fn)) (not (nil? regex)))
          (let [params (params-from-steps regex step-sentence)
                result (execute-fn-for-step step-sentence fn prev-return params)]
            (recur (rest step-sentences)
                   result
                   (conj scenario-acc [step-sentence result])))
          scenario-acc))
      scenario-acc)))

;;TODO include deftest with the macro define in the above and with test-ns-hook for running the test in the correct order
(defmulti exec-spec
  "Read the spec and execute each step with the code setup by the defgiven, defwhen and defthen macro"
  ;; the dispatch fn do that on the type of the parameter but
  ;; if it's string, it first check if it's the name of the file
  ;; otherwise considers it's the spec itself
  (fn [spec]
    (letfn [(handle-file-or-dir [file-or-dir]
              (if (.isFile file-or-dir)
                :file
                (if (.isDirectory file-or-dir)
                  :dir)))]
      (if (= (type spec) java.lang.String)
        (if (.exists (java.io.File. spec))
          (handle-file-or-dir (java.io.File. spec))
          :spec-as-str)
        (if (= (type spec) java.io.File)
          (handle-file-or-dir spec)
          (throw (RuntimeException. (str "type " (type spec) "for spec not accepted (only string or file)")))))))
  :default :file)

(defn get-spec-files [basedir]
  (letfn [(find-spec-files [basedir]
            (FileUtils/listFiles
             basedir
             (into-array ["story" "feature"])
             true ;;recursive
             ))]
    (case (str (type basedir))
      "class java.lang.String" (if (.exists (java.io.File. basedir))
                         (find-spec-files (java.io.File. basedir ))
                         (throw (RuntimeException. (str basedir " doesn't exists in path: " (java.lang.System/getProperty "user.dir")))))
      "class java.io.File" (find-spec-files basedir))))


(defmethod exec-spec
  :dir
  [spec-dir]
  (doseq [spec-file (get-spec-files spec-dir)]
    (exec-spec spec-file)))

(defmethod exec-spec
  :file
  [spec-file]
  (exec-spec (slurp spec-file)))

(defmethod exec-spec
  :spec-as-str
  [spec-str]
  ;;for each scenarios
  (do (doseq [before-fn @before] (before-fn))
      (let [spec-parse-tree (gherkin-parser spec-str)]
        (if (insta/failure? spec-parse-tree)
          (do (println "The supplied spec contains a parse error, please fix it,
                        if you tried to supply a filename it was not found,
                        please verify the relative path (btw that test get executed in the following dir:"
                       (java.lang.System/getProperty "user.dir") ")")
              (insta/get-failure spec-parse-tree))
          (loop [scenarios (scenarios-ast spec-parse-tree)
                 spec-acc [(do (let [narrative (narrative-str (narrative-ast spec-parse-tree))]
                                 (println narrative)
                                 narrative))]]
            (if-let [scenario-ast (first scenarios)]
              (if (scenario-with-examples? scenario-ast)
                (let [exec-result (exec-scenario-with-examples scenario-ast)]
                  (recur (rest scenarios) (conj spec-acc exec-result)))
                (let [exec-result (exec-scenario scenario-ast)]
                  (recur (rest scenarios) (conj spec-acc exec-result))))
              (do (print "\n")
                  (doseq [after-fn @after] (after-fn))
                  spec-acc)))))))

(defn exec-specs
  ([dirs-or-specs]
   (if (coll? dirs-or-specs)
     (doseq [dir-or-spec dirs-or-specs]
       (exec-spec dir-or-spec))
     (exec-spec dirs-or-specs)))
  ([]
   (let [basedir (java.lang.System/getProperty "user.dir")]
     (exec-specs basedir))))


(defn- find-stories-available []
  (let [basedir (java.lang.System/getProperty "user.dir")]))
