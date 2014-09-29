Scenario: a scenario that test spexec using spexec
Given the step function: (defgiven (re-pattern "this scenario in a file named '(.*)'$") [_ feature-file-name] [feature-file-name])
Given the step function: (defwhen (re-pattern "I run the scenarios with '(.+)'$") [prev-ret my-data] (conj prev-ret (str "processed" my-data)))
Given the step function: (defthen (re-pattern "I should get '(.+)' from scenario file '(.*)' returned from the previous when step$") [prev-ret expected-data scenario-file] (clojure.test/is (= (last prev-ret) expected-data))(clojure.test/is (= (first prev-ret) scenario-file)))
Given this scenario in a file named 'resources/spexec.feature'
When I run the scenarios with 'mydatavalues'
Then I should get 'processedmydatavalues' from scenario file 'resources/spexec.feature' returned from the previous when step
