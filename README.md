
<a href="https://github.com/jgrodziski/scenari">
  <img src="https://cdn.rawgit.com/jgrodziski/scenari/68d74b6d/scenari.svg" width="100%" height="250">
</a>

# Scenari - Executable Specification / BDD in Clojure

Scenari is an Executable Specification [Clojure](http://clojure.org/) library aimed at writing and executing usage scenarios following the [Behavior-Driven Development](http://en.wikipedia.org/wiki/Behavior-driven_development) - BDD - style. It has an [external DSL](http://www.martinfowler.com/bliki/DomainSpecificLanguage.html), following the [gherkin grammar](https://github.com/cucumber/cucumber/wiki/Gherkin) (in short: Given/When/Then), and execute each scenario's _steps_ with associated Clojure code.

This library was previously named "Spexec" (https://github.com/zenmodeler/spexec) and under the org name of a [side project I closed recently](http://zenmodeler.com), I moved it under [my personal github id](https://github.com/jgrodziski) and renamed it to "Scenari" because of Clojure Spec and the confusion it can generates with such close names.

* [Installation](#installation)
* [Basic Usage](#basic-usage)
	* [Write Scenarios in plain text]()
	* [Map steps to Clojure code]()
	* [Execute Specification and get a report]()
  * [Define "before" and "after" code]()
* [Documentation](#documentation)
* [Rationale](#rationale)
* [ToDoS](#todos)

## Installation

```clojure
;;add this dependency to your project.clj file
[io.defsquare/scenari "2.0.0-alpha"]
;;or deps.edn
{
 io.defsquare/scenari {:mvn/version "2.0.0-alpha"}
}
;;then in your ns statement
(:require [scenari.v2.core :as scenari :refer [defgiven defwhen defthen deffeature]])
```

[![Clojars Project](https://img.shields.io/clojars/v/io.defsquare/scenari.svg)](https://clojars.org/io.defsquare/scenari)

## Basic Usage


### Write Scenarios in plain text
First, write your scenarios in plain text using the [Gherkin grammar]((https://github.com/cucumber/cucumber/wiki/Gherkin)) in a file or String :
You can add a "narrrative" for all your scenarios with the story syntax at the beginning of the story file (As a role I want to do something In order to get value)
```Gherkin
Scenario: create a new product
# this is a comment
When I create a new product with name "iphone 6" and description "awesome phone"
Then I receive a response with an id 56422 and a location URL
# this a second comment
# on two lines
When I invoke a GET request on location URL
Then I receive a 200 response

Scenario: get product info
When I invoke a GET request on location URL
Then I receive a 200 response
```

### Declaring a specification 

```clojure
(require 'scenari.v2.core :refer [deffeature])

(deffeature my-specification "./path/to/feature/file") ;; define deftest bound to symbol 'my-specification', put the specification as clojure data-structure in metadata and return the specification
;;=> 
;;{:scenarios [{:id "0ef9b8a9-e035-4ae2-96c4-662c0b8988de",
;;		:pre-run (),
;;		:post-run (),
;;		:scenario-name " create a new product",
;;		:steps [{:sentence-keyword :when,
;;			 :sentence "I create a new product with name \"iphone 6\" and description \"awesome phone\"",
;;			 :raw "When I create a new product with name \"iphone 6\" and description \"awesome phone\"",
;;			 :params [{:type :value, :val "iphone 6"} {:type :value, :val "awesome phone"}],
;;			 :order 0,
;;			 :glue nil}
;;			 ...steps]}
;;	        ...scenarios ],
;; :pre-run ()}
```
### Write glue-code

Then write the code that will get executed for each scenario steps:

```clojure

(require 'scenari.v2.core :refer [defwhen defthen])

(defwhen #"I create a new product with name \"([^\"]*)\" and description \"([^\"]*)\""
[_ name desc]
  (println "executing my product creation function with params " name desc)
  (let [id (UUID/next.)]
  	{:id (UUID/next. ) 
  	 :name name 
  	 :desc desc 
  	 :qty (rand-int 50) 
  	 :location-url (str "http://example.com/product/" id)}))


(defthen #"I receive a response with an id \"([^\"]*)\""
  [_ id]
  (println (str "executing the assertion that the product has been created with the id " id))
  id)
```

**Tips**: you can get a function snippet generated for you when executing the spec without step function. Think about enclosing with quote 'your data' in step sentence to get them detected by the parser and it'll generate a step function skeleton in the output with the correct regex group.
Example: 

Executing the specification with the step sentence without any matching function:

```gherkin
When I create a new product with name "iphone 6" and description "awesome phone"
```

will generate in the stdout the following step function skeleton:

```
Missing step for : When I create a new product with name "iphone 6" and description "awesome phone"
(defwhen #"I create a new product with name \"([^\"]*)\" and description \"([^\"]*)\""  [state arg0 arg1]  (do "something"))
```

### how to get data from the scenario into your step function

Every group the regex will find (everything enclosed in parens () in your regex) will be transmitted as a string to your step function params with the same left-to-right order, BUT the data is first evaluated as clojure.edn data string (see [clojure.edn/read-string](https://clojure.github.io/clojure/clojure.edn-api.html)) and IF it is a Clojure data structure ((coll? evaluated-data) returns true), THEN it will be transmitted evaluated as a param to the step function.

**Tips**: the map will be detected by the parser and it'll generate a step function skeleton in the output with the correct regex group.


### Execute scenario(s)
 There is three-way to execute scenarios, depending on your situation

#### Tree execution
Declaring your specification using `scenari.v2.core/deffeature` returns the parsed specification as clojure data structure. By using `scenari.v2.core/run-scenario`, the specification as data will be ran

```Clojure
(require 'scenari.v2.core :refer [run-feature run-features])
(run-feature #'my-specification)

;;OR
(require 'scenari.v2.core :refer [run-scenarios])
(run-features #'my-specification)
```

The execution report will be returned, rely on same clojure data-structure returned by `scenari.v2.core/deffeature`. Will set : 
- final `:status` of scenario(s) execution
- step `:status` as `pending` when not executed, `failed` when assertions fail or exception thrown, `success` at last
- step `:input-state` as the value returned by the previous step executed (empty map for the first one)  
- step `:output-state` as the value returned by the current step

This method is useful for debugging.

#### Clojure-test execution
Use clojure-test reporting system by printing execution. 

```clojure
(require 'scenari.v2.test :refer [run-feature])
(run-feature #'my-specification)
;; ________________________
;; Feature :
;; 
;; Testing scenario :
;; When I create a new product with name "iphone 6" and description "awesome phone"         (from /#"")
;; Step failed
;; create a new product failed at step  of
;; 
;; Testing scenario :
;; When I invoke a GET request on location URL         (from scenari.v2.glue/#"I invoke a GET request on location URL")
;; =====> {:kix "lol"}
;; Then I receive a 200 response         (from /#"")
;; Step failed
;; get product info failed at step  of
;; 
;; ________________________
;; 
;; 
;; Ran 1 features containing 2 scenarios.
;; 0 success, 2 fail.
;; =>
;; {:test 0,
;;  :pass 0,
;;  :fail 2,
;;  :error 0,
;;  :executed-features 1,
;;  :executed-scenarios 2,
;;  :scenarios-failed 2,
;;  :feature-failed 1}
;;
```
Useful to integrate a feature in a clojure test namespace


#### Kaocha runner
Kaocha is a test runner and handle test phase lifecycle. 

By defining a test type in your kaocha configuration file (`tests.edn` by default) like this  
```clojure
#kaocha/v1
        {:tests [{:id                                :scenario
                  :type                              :kaocha.type/scenari
                  :kaocha/source-paths               ["src"]
                  :kaocha/test-paths                 ["test/scenario"]
                  :kaocha.type.scenari/glue-paths    ["test/scenario/glue"]
                  :kaocha.type.scenari/feature-paths ["resources/scenarios"]}]}
```
You are able to launch your scenario using kaocha repl utility function

```clojure
(require 'kaocha.repl :as krepl)
(krepl/run :scenario)

;; Testing scenario :  create a new product
;;   When I invoke a GET request on location URL         (from scenari.v2.glue/#"I invoke a GET request on location URL")
;;   When I create a new product with name "iphone 6" and description "awesome phone" with properties         (from scenari.v2.glue/#"I create a new product with name \"(.*)\" and description \"(.*)\" with properties")
;;   Then I receive a response with an id 56422         (from scenari.v2.glue/#"I receive a response with an id 56422")
;;   Then a location URL         (from scenari.v2.glue/#"a location URL")
;; 
;; 
;; 1 tests, 1 assertions, 0 failures.
;; => #:kaocha.result{:count 1, :pass 1, :error 0, :fail 0, :pending 0}
```
Suitable when using kaocha to manage test lifecycle.

### Using hooks
By providing an options maps in `scenari.v2.core/deffeature`, you can specify function which execute :
- `:pre-run` before feature execution
- `:post-run` after feature executed
- `:pre-scenario-run` before each scenario execution
- `:post-scenaro-run` after each scenario executed
Example:

```Clojure
(require 'scenari.v2.core :refer [deffeature])

(defn before-all [] (prn "init feature components"))
(defn before-each [] (prn "init scenario components"))
(defn after-each [] (prn "clean scenario side effects"))
(defn clean [] (prn "reset and shut down components"))

(deffeature my-specification "./path/to/feature/file"
			{:pre-run           [#'before-all]
			 :pre-scenario-run  [#'before-each]
			 :post-scenario-run [#'after-each]
			 :post-run          [#'clean]})
```

### Provide an initial state
For each scenario execution, an initial state can be provided within the options map of `deffeature`.

Example:

```clojure
(deffeature my-specification "./path/to/feature/file"
			{:default-scenario-state {:foo "bar"}})
```
By default, the scenario state is an empty map `{}`.

## Documentation 

### Declaring same step (glue-code) but different namespace
Sometimes, you have to declare the same step (using the regex matcher) but for different context (domain or component level for exemple).

TODO Put an exemple about step proximity resolution

### Macros (deprecated)
There are 3 macros available for given/when/then:

```clojure
;; a regex for matching a step in the scenarios
;; a params vector: 
;;    the first param is the return of the previous step (nil if first step)
;;    then one param for each regex group (aka. something in parens (...)) you define.
;; a body function.

(defgiven regex params body)
(defwhen regex params body)
(defthen regex params body)
```

Macros are here for convenience, plain-old function are also available, you have to provide the step execution function as parameters with the function having groups count + 1 parameters (one for the previous step return and one params for each groups in the regex).

```clojure
(ns mystuff
  (:require [spexec :as spec]))
...
(Given regex fn)
(When regex fn)
(Then regex fn)
```

### Chaining steps 
Steps often produce side effect or retrieve some stuffs (fn, data) to be used in the next ones, you can store your state in your code or in the scenario itself, but I think an easier mechanism is to think of the steps like a chain and pass a data structure from the return of a step to the input of the next one (very similar to [ring handlers](https://github.com/ring-clojure/ring/wiki/Concepts) or chain of responsibility for instance). So, each step function's return is taken as the input for the next step as the first argument (you can name it `_` if you don't need it). A good practice would be to use a map or vector and then destructure it as the first param of the next step, like :

```clojure
(defwhen #"my sentence to be matched with (.*) and (.*)" 
         [[key1-in-previous-result k2] param1 param2] 
         (do-something param1 k2 param2) ...)
```

### Advanced usage (deprecated)

I use Spexec to test Spexec (yes it eats its own dog food, pretty amazing :-) only a dynamic language like Lisp can do that as easily), only the bootstrap step "Given the step function" is needed:

```gherkin
Scenario: a scenario that test spexec using spexec
Given the step function: (defgiven (re-pattern "^this scenario in a file named (.*)") [_ feature-file-name] [feature-file-name])
Given the step function: (defwhen (re-pattern "^I run the scenarios with '(.+)'") [prev-ret my-data] (conj prev-ret (str "processed" my-data)))
Given the step function: (defthen (re-pattern "^I should get '(.+)' from scenario file '(.*)' returned from the previous when step") [prev-ret expected-data scenario-file] (clojure.test/is (= (last prev-ret) expected-data))(clojure.test/is (= (first prev-ret) scenario-file)))
Given this scenario in a file named resources/spexec.feature
When I run the scenarios with 'mydatavalues'
Then I should get 'processedmydatavalues' from scenario file 'resources/spexec.feature' returned from the previous when step
```

```clojure
(defgiven #"the step function: (.+)" [_ step-fn]
   (eval (read-string step-fn)))
```

### Run the scenarios with each steps

### Logging

## Rationale

I'm used to [JBehave](http://jbehave.org/) and I wanted a BDD framework with an [external DSL](http://www.martinfowler.com/bliki/DomainSpecificLanguage.html) following the [gherkin grammar](https://github.com/cucumber/cucumber/wiki/Gherkin) but also with an easy and fast setup and with steps written in [Clojure](http://clojure.org/). The previous BDD attempt I known in Clojure were all with an [internal DSL](http://www.martinfowler.com/bliki/DomainSpecificLanguage.html). I prefer an external one because I think it's easier to share the scenarios with a domain expert. I you prefer an internal DSL BDD Framework, have a look at [Speclj](http://speclj.com/).

Proper compatibility with traditional clojure.test interfaces is needed, for instance we could make the following relations between Scenari/BDD concepts and clojure.test:
- A scenario execution is like a `deftest`. Particularly, we need to associate steps with Gherkin scenarios defined in one or more feature files. For that we would define this association between steps (`defgiven`, `defwhen` and `defthen`) and scenarios with `(defscenarios "my.feature")`. The execution would then be run with `(run-scenarios)` much like `(run-tests)`. In case of examples table used to feed the scenario with data, each data row would be associated with a new testing context for each steps (the `testing` context description would be the steps sentence with all data placeholder replaced with the actual ones).
- A scenario's step is like a `testing` context inside a `deftest`. 
Also, steps and scenarios association must be isolated within namespace to avoid collisions when the same scenarios are used with different steps (like ones for domain test, others for integration testing, etc.).
The compatibility with clojure.test would also be with its various reports available (`:pass`, `:fail`, etc.) with reports specific to narrative, scenarios and steps. 
Concerning assertion, steps could contains `clojure.test/is` assertions or throws exception that will be handled properly like clojure.test ones. 

I use the [regex facility](http://clojure.org/other_functions) provided by Clojure (#"regex expression"), not the most readable with all that parens, sharps, double-quote, etc. but the most supple when you need to extract specific data from your sentence. 
The gherkin grammar parser is written with the amazing [Instaparse](https://github.com/Engelberg/instaparse) library (I thumbs up for the ClojureScript port by the way!).

I did a presentation of the internals of the library at the Clojure Paris User Group and the slides are here: ["Anatomy of a BDD Execution Library in Clojure"](https://speakerdeck.com/jgrodziski/anatomy-of-a-bdd-execution-library-in-clojure).

## TODOS

* stop-on-failure? as an option for execution

## License

Scenari is released under the terms of the [MIT License](http://opensource.org/licenses/MIT).

Copyright © 2020 Jérémie Grodziski jeremie@grodziski.com

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
