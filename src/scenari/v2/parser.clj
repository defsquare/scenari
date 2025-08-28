(ns scenari.v2.parser
  (:require [instaparse.core :as insta]))

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

(def gherkin (insta/parser
                      (str "
           SPEC = <whitespace?> <comment?> annotations? narrative? <whitespace?> <comment?> scenarios
           narrative          = <'Narrative: '|'Feature: '> <whitespace?> #'.*' <eol>? (as_a I_want_to in_order_to |
                                                                                       as_a I_want_to so_that | in_order_to as_a I_want_to |
                                                                                       as_a in_order_to I_want_to)?
           annotations        = (<whitespace?> annotation <whitespace?>)*
           annotation         = <'@'> #'\\w+'
           in_order_to        = <whitespace>? <'In order to '> #'.*' <eol>
           as_a               = <whitespace>? <'As a '> #'.*' <eol>
           I_want_to          = <whitespace>? <'I want to '> #'.*' <eol>
           so_that            = <whitespace>? <'So that '> #'.*' <eol>
           scenarios          = (scenario <eol?> <eol?>)*
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
           <eol>              = #'\r?\n'
           scenario_sentence  = #'.*'
           step_sentence      = step_keywords sentence (<eol> tab_params)?
           sentence           = #'.*'
           examples           = <whitespace?> examples-keywords <eol> header row* <eol?>
           <examples-keywords>= <" (kw-translations :examples) ">
           tab_params         = <whitespace?> header row* <eol?>
           header             = <whitespace?> (<'|'> column_name)+ <'|'> <eol?>
           <column_name>      = <whitespace?> #'[^|]*' <whitespace?>
           row                = <whitespace?> (<'|'> <whitespace?> value )+ <whitespace?> <'|'> <eol?>
           <value>            = #'[^|]*'
           word               = #'[\\p{L}$€]+'
           number             = #'[0-9]+'
           ")))

(def sentence (insta/parser
                (str "SENTENCE         = <whitespace>? (words | data_group | parameter)* <eol>?
                             words            = #'[a-zA-Z./\\_\\-\\'èéêàûù ]+'
                             <parameter_name> = #'[a-zA-Z\"./\\_\\- ]+'
                             parameter        = <'<'> parameter_name <'>'> | <'${'> parameter_name <'}'>
                             string           = <'\"'> #'[^\"]*' <'\"'>
                             number           = #'\\d+'
                             <data_group>     = string | number | map | vector
                             map              = #'\\{[a-zA-Z0-9\\-:,./\\\" ]+\\}'
                             elements         = (#'\".+\"|[0-9]+' <whitespace>?)*
                             vector           = <'['> elements <']'>
                             <whitespace>     = #'\\s+'
                             <value>          = #'[a-zA-Z0-9+ ]*'
                             whitespace       = #'\\s+'
                             eol              = #'\r?\n'")))

(def step (insta/parser
                   (str "STEP             = <whitespace?> step_keyword (words | data_group | parameter)* <eol>?
                             given            = <" (kw-translations :given) ">
                             when             = <" (kw-translations :when) ">
                             then             = <" (kw-translations :then) ">
                             and              = <" (kw-translations :and) ">
                             words            = #'[a-zA-Z./\\_\\-\\'èéêàûù ]+'
                             <parameter_name> = #'[a-zA-Z\"./\\_\\- ]+'
                             parameter        = <'<'> parameter_name <'>'> | <'${'> parameter_name <'}'>
                             string           = <'\"'> #'[^\"]*' <'\"'>
                             number           = #'\\d+'
                             <data_group>     = string | number | map | vector
                             map              = #'\\{[a-zA-Z0-9\\-:,./\\\" ]+\\}'
                             elements         = (#'\".+\"|[0-9]+' <whitespace>?)*
                             vector           = <'['> elements <']'>
                             <step_keyword>   = given | when | then | and
                             <whitespace>     = #'\\s+'
                             <value>          = #'[a-zA-Z0-9+ ]*'
                             whitespace       = #'\\s+'
                             eol              = #'\r?\n'")))
