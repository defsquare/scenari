
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
[scenari "1.4.2"]
;;or deps.edn
{
scenari {:mvn/version "1.4.2"}
}
;;then in your ns statement
(:require [scenari.core :as scenari :refer [defgiven defwhen defthen run-scenarios]])

```

[![Clojars Project](http://clojars.org/scenari/latest-version.svg)](http://clojars.org/scenari)

## Basic Usage


### Write Scenarios in plain text
First, write your scenarios in plain text using the [Gherkin grammar]((https://github.com/cucumber/cucumber/wiki/Gherkin)) in a file or String :
You can add a "narrrative" for all your scenarios with the story syntax at the beginning of the story file (As a role I want to do something In order to get value)
```Cucumber
Scenario: create a new product
# this is a comment
When I create a new product with name 'iphone 6' and description 'awesome phone'
Then I receive a response with an id 56422 and a location URL
# this a second comment
# on two lines
When I invoke a GET request on location URL
Then I receive a 200 response

Scenario: get product info
When I invoke a GET request on location URL
Then I receive a 200 response
```

### Map steps to Clojure code

Then write the code that will gets executed for each scenario steps:

```clojure

(require 'scenari.core :refer [defgiven defwhen defthen])

(defwhen #"I create a new product with name '(.*)' and description '(.*)'"
[_ name desc]
  (println "executing my product creation function with params " name desc)
  (let [id (UUID/next.)]
  	{:id (UUID/next. ) 
  	 :name name 
  	 :desc desc 
  	 :qty (rand-int 50) 
  	 :location-url (str "http://example.com/product/" id)}))


(defthen #"I receive a response with an id (.+)"
  [_ id]
  (println (str "executing the assertion that the product has been created with the id " id))
  id)
```

**Tips**: you can get a function snippet generated for you when executing the spec without step function. Think about enclosing with quote 'your data' in step sentence to get them detected by the parser and it'll generate a step function skeleton in the output with the correct regex group.
Example: 

Executing the specification with the step sentence without any matching function:

```cucumber
When I create a new product with name '(.*)' and description '(.*)'
```

will generate in the stdout the following step function skeleton:

```clojure
No function found for step you may add: 
(defwhen #"I create a new product with name '(.*)' and description '(.*)'"  [arg0 arg1]  (do "something"))
```

### how to get data from the scenario into your step function

Every group the regex will find (everything enclosed in parens () in your regex) will be transmitted as a string to your step function params with the same left-to-right order, BUT the data is first evaluated as clojure.edn data string (see [clojure.edn/read-string](https://clojure.github.io/clojure/clojure.edn-api.html)) and IF it is a Clojure data structure ((coll? evaluated-data) returns true), THEN it will be transmitted evaluated as a param to the step function.

**Tips**: the map will be detected by the parser and it'll generate a step function skeleton in the output with the correct regex group.


### Execute Specification

```Clojure
(run-scenario "resources/product-catalog.feature")

;;OR

(run-scenarios "resources")
```

Get a report of the execution as a Clojure data structure returned by the ```run-scenario``` function

```Clojure
{"scenario 1 sentence" 
    [["step sentence 1.1 executed" {:result "data structure"}]
     ["step sentence 1.2 executed" {:result "data structure"}]
     ["step sentence 1.3 executed" {:result "data structure"}]]
 "scenario 2 sentence" 
    [["step sentence 2.1 executed" {:result "data structure"}]
     ["step sentence 2.2 executed" {:result "data structure"}]
     ["step sentence 2.3 executed" {:result "data structure"}]]}
```

### Define "Before" and "After" code

```defbefore``` and ```defafter``` are two functions that take another function as a parameter that will get executed respectively before and after the scenarios execution.
It's useful to setup a resource (like an http server or a database) and shutdown it after all scenarios get executed. You can define several before or after functions, they will all be executed before or after scenarios, though with no guarantee for their execution order.

Example:

```Clojure
(defbefore (fn [] (println "that anonymous function gets executed before all scenarios")))
```

## Documentation

### Macros
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
Steps often produce side effect or retrieve some stuffs (fn, data) to be used in the next ones, you can store your state in your code or in the scenario itself, but I think an easier mechanism is to think of the steps like a chain and pass a data structure from the return of a step to the input of the next one (very similar to [ring handlers](https://github.com/ring-clojure/ring/wiki/Concepts) or chain of responsibility for instance). So, each step functio's return is taken as the input for the next step as the first argument (you can name it _ if you don't need it). A good practice would be to use a map or vector and then destructure it as the first param of the next step, like :

```clojure
(defwhen #"my sentence to be matched with (.*) and (.*)" 
         [[key1-in-previous-result k2] param1 param2] 
         (do-something param1 k2 param2) ...)
```

### Advanced usage

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

Copyright © 2019 Jérémie Grodziski jeremie@grodziski.com

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
