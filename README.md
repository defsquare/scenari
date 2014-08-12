# Spexec - A Clojure Library for [Behavior Driven Development](http://en.wikipedia.org/wiki/Behavior-driven_development) (BDD)

## Rationale

I wanted a BDD framework with an external DSL following the [gherkin grammar](https://github.com/cucumber/cucumber/wiki/Gherkin). The previous BDD attempt I known in Clojure were all with an internal DSL. I prefer an external one that is easier to share with a domain expert.

I use the regex facility provided by Clojure (#"regex expression"), not the most readable with all that parens, sharps, double-quote, etc. but the most supple when you need to extract specific data from your sentence.

## Usage

There are 3 macros available: ```clojure defgiven```, ```clojure defwhen```, ```clojure defthen```
They wants 3 params: a regex for matching a step in the scenarios and a params vector and body function.
Your regex should "group" expression with parens if you want to extract value as string that will be provided to your step functions.

### chaining steps
Steps often produce side effect or retrieve some stuffs (fn, data) to be used in the next ones, you can store your state in your code or in the scenario itself, but I think an easier mechanism is to think of the steps like a chain and pass a data structure from the return of a step to the input of the next one (very similar to [ring handlers](https://github.com/ring-clojure/ring/wiki/Concepts) for instance). So, each step functio's return is taken as the input for the next step as the first argument (you can name it _ if you don't need it). A good practice would be to use a map or vector and then destructure it as the first param of the next step, like :

```clojure
(defwhen #"my sentence to be matched with (.*) and (.*)" 
		 [[key1-in-previous-result k2] param1 param2] 
		 	(do-something param1 k2 param2) ...)
```

I use Spexec to test Spexec (yes it eats its own dog food, pretty amazing :-) I think only a Lisp language allow to do that), only the bootstrap step "Given the step function" is needed:

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

## License

Distributed under the Eclipse Public License either version 1.0.