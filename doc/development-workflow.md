# Scenari Development Workflow

This document outlines the development workflow when using Scenari for Behavior-Driven Development (BDD) in Clojure projects. It covers the complete journey from writing scenarios to executing and maintaining them.

## Overview

The Scenari development workflow follows these main steps:

1. Write scenarios in Gherkin format (`.feature` files)
2. Define feature references using `deffeature`
3. Implement step definitions (glue code) 
4. Execute and validate the scenarios
5. Refine and iterate

Let's explore each step in detail.

## 1. Writing Feature Files

Feature files use the Gherkin syntax and typically have a `.feature` extension. They describe behaviors from a user's perspective.

### Basic Structure

```gherkin
Feature: Shopping Cart
  As a customer
  I want to manage items in my cart
  So that I can purchase what I need

  Scenario: Add item to empty cart
    Given I have an empty shopping cart
    When I add "Clojure Programming" book to the cart
    Then my cart should contain 1 item
    And the item should be "Clojure Programming" book
```

### Key Components

- **Feature**: The overall functionality being described
- **Narrative**: "As a..., I want to..., So that..." pattern explaining the purpose
- **Scenarios**: Specific examples of the feature in action
- **Steps**: Individual actions and assertions (Given/When/Then/And)

### Tips for Writing Good Scenarios

- Focus on business value and user perspective
- Keep scenarios concise and focused on a single behavior
- Use declarative style ("what" rather than "how")
- Maintain consistency in terminology
- Use data tables for multiple examples

### Example with Data Tables

```gherkin
Scenario: Calculate discounts
  Given the following products in catalog:
    | product    | price | category |
    | Keyboard   | 100   | hardware |
    | Mouse      | 50    | hardware |
    | Clojure    | 40    | book     |
  When I apply the "SUMMER10" discount code
  Then the prices should be:
    | product    | discounted_price |
    | Keyboard   | 90               |
    | Mouse      | 45               |
    | Clojure    | 36               |
```

### Example with Scenario Outline

```gherkin
Scenario Outline: Apply tax based on location
  Given a product with price <base_price>
  When shipping to <location>
  Then the final price should be <final_price>

  Examples:
    | base_price | location | final_price |
    | 100        | US       | 108         |
    | 100        | EU       | 120         |
    | 100        | AU       | 110         |
```

## 2. Defining Feature References

After writing the feature file, you need to reference it in your Clojure code using `deffeature`.

```clojure
(ns my-project.shopping-cart-test
  (:require [clojure.test :refer :all]
            [scenari.v2.core :as scenari :refer [deffeature]]))

;; Reference to the feature file
(deffeature shopping-cart "resources/features/shopping_cart.feature")
```

This creates a test that can be executed by Clojure's test runner. The `deffeature` macro:

1. Loads and parses the feature file
2. Creates a Clojure test that will execute all scenarios in the feature
3. Associates the feature with the current namespace for step discovery

### Configuration Options

You can customize the feature execution with options:

```clojure
(deffeature shopping-cart "resources/features/shopping_cart.feature"
  {:pre-run [#'setup-database]
   :post-run [#'teardown-database]
   :pre-scenario-run [#'setup-cart]  
   :post-scenario-run [#'cleanup-cart]
   :default-scenario-state {:user-id "test-user"}})
```

## 3. Implementing Step Definitions (Glue Code)

Step definitions (also called "glue code") connect the Gherkin steps with actual Clojure code. Scenari provides macros for defining these connections.

### Basic Step Definitions

```clojure
(ns my-project.shopping-cart-test
  (:require [clojure.test :refer :all]
            [scenari.v2.core :as scenari :refer [deffeature defgiven defwhen defthen]]))

(defgiven "I have an empty shopping cart" [state]
  (assoc state :cart []))

(defwhen "I add {string} book to the cart" [state book-title]
  (update state :cart conj {:title book-title :type :book}))

(defthen "my cart should contain {number} item" [state item-count]
  (is (= item-count (count (:cart state))))
  state)

(defthen "the item should be {string} book" [state book-title]
  (is (= book-title (-> state :cart first :title)))
  state)
```

### Parameter Handling

Scenari supports various parameter types in step definitions:

- `{string}`: Matches a quoted string and passes it as a String
- `{number}`: Matches a number and passes it as a Number
- Table data: Automatically passed as a vector of maps

### Working with Tables

```clojure
(defgiven "the following products in catalog:" [state table-data]
  (assoc state :products 
    (into {} (map (fn [row] [(:product row) row]) table-data))))
```

### State Passing Between Steps

Each step function receives the state from the previous step and must return the (possibly modified) state for the next step. This allows for data to flow through your scenario.

### Best Practices for Step Definitions

- Keep step functions focused and small
- Use descriptive step names
- Include meaningful assertions
- Don't couple steps too tightly to implementation details
- Store state in a map for flexibility

## 4. Execution Flow

When a feature is executed, Scenari performs the following steps:

1. **Feature Loading**: Parse the feature file into an AST
2. **Feature Transformation**: Convert the AST into an executable structure
3. **Scenario Execution**: For each scenario:
   - Initialize the scenario state (empty map or provided default)
   - Execute any pre-scenario hooks
   - For each step:
     - Find the matching step definition
     - Execute the step function with the current state and parameters
     - Capture the result and status
     - Pass the result state to the next step
   - Execute any post-scenario hooks
4. **Reporting**: Collect results and generate reports

### Step Matching Process

The step matching process is a key part of Scenari:

1. Convert the step text from the feature file into a searchable format
2. Look for step definitions that match the pattern
3. If multiple matches are found, use namespace proximity to select the best match
4. Extract parameters from the step text
5. Execute the matching function with state and parameters

## 5. Execution and Validation

### Running Tests

The simplest way to execute Scenari tests is through the standard Clojure test runner:

```bash
clojure -M:test       # Run all tests
```

Scenari integrates with Kaocha for more advanced test execution:

```bash
clojure -M:test -m kaocha.runner                  # Run all tests
clojure -M:test -m kaocha.runner --focus my-test  # Run specific test
```

### Test Output

The test output will show each scenario and step execution:

```
--- my-project.shopping-cart-test ---
Feature: Shopping Cart

Testing scenario: Add item to empty cart
  Given I have an empty shopping cart
  When I add "Clojure Programming" book to the cart
  Then my cart should contain 1 item
  And the item should be "Clojure Programming" book

PASS: my-project.shopping-cart-test/shopping-cart
```

### Debugging Tests

When a step fails, Scenari provides information about the failure:

```
Step failed: "my cart should contain 1 item"
Expected: 1
  Actual: 0
```

The state passed between steps can be examined in the test output when there's a failure.

## 6. Advanced Features

### Namespace Resolution

When multiple step definitions match a step, Scenari uses namespace proximity to choose:

1. Steps in the same namespace as the feature have highest priority
2. Steps in namespaces with more shared segments have higher priority
3. If equal priority, an error is raised to avoid ambiguity

### Custom Parameter Types

You can extend Scenari with custom parameter types by creating specialized regex patterns in your step definitions.

### Hooks and Lifecycle Management

Scenari supports several hook points for setup and teardown:

- Pre-feature hooks: Run once before the entire feature
- Post-feature hooks: Run once after the entire feature
- Pre-scenario hooks: Run before each scenario
- Post-scenario hooks: Run after each scenario

## Conclusion

The Scenari development workflow provides a structured approach to Behavior-Driven Development in Clojure. By following the pattern of writing features, defining glue code, and executing tests, you can create living documentation that verifies your application's behavior.

Remember that the true value of BDD comes from the collaborative process—use feature files as a communication tool between developers, testers, and domain experts to ensure a shared understanding of requirements and behaviors.