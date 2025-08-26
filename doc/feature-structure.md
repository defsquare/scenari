# Scenari Feature Data Structure Documentation

This document describes the internal data structure of a Scenari feature after parsing from Gherkin text. Understanding this structure is helpful when extending or customizing Scenari.

## Top-Level Structure

A feature is represented as a map with the following keys:

```clojure
{:scenarios [...]       ; Vector of scenario maps
 :feature [...]         ; Optional narrative elements
 :annotations #{...}    ; Optional annotations (tags)
 :pre-run [...]         ; Hook functions to execute before feature
 :status :success/:fail ; Status after execution
}
```

## Narrative/Feature Section

The `:feature` key contains details about the narrative section:

```clojure
{:feature ["Feature title" 
           [:as_a "role"]
           [:I_want_to "goal"]
           [:so_that "benefit"]]}
```

## Annotations

Annotations (tags) are stored as a set of strings:

```clojure
{:annotations #{"smoke" "regression" "api"}}
```

## Scenarios

Each scenario is represented as a map within the `:scenarios` vector:

```clojure
{:id "uuid-string"           ; Unique identifier
 :scenario-name "Name"       ; The scenario title
 :steps [...]                ; Vector of step maps
 :pre-run [...]              ; Functions to run before scenario
 :post-run [...]             ; Functions to run after scenario
 :default-state {}           ; Initial state for the scenario
 :status :success/:fail/:pending ; Execution status
}
```

## Steps Structure

Each step within a scenario is represented as a map:

```clojure
{:sentence-keyword :given/:when/:then/:and  ; Step type
 :sentence "Step text"                      ; The actual step text
 :raw "Given Step text"                     ; Full text with keyword
 :order 0                                   ; Position in scenario
 :glue {...}                                ; Matched step definition
 :params [...]                              ; Extracted parameters
 :status :success/:fail/:pending            ; Execution status
 :input-state {}                            ; State before execution
 :output-state {}                           ; State after execution
 :exception {...}                           ; If step failed
}
```

## Parameters

Parameters extracted from steps come in two types:

```clojure
;; Value parameters (extracted from step text)
{:type :value, :val "some string"} 
{:type :value, :val 42}

;; Table parameters
{:type :table, 
 :val [{:header1 "value1", :header2 "value2"}, 
       {:header1 "value3", :header2 "value4"}]}
```

## Glue Metadata

The `:glue` key contains information about the matched implementation function:

```clojure
{:step "I do something {string}"  ; Pattern to match
 :ns user.namespace               ; Function namespace
 :name function-name              ; Function name
 :ref #'user.namespace/function   ; Reference to actual function
 :warning "Warning message"       ; Optional warning
}
```

## Example Execution Flow

1. Feature is parsed from text using `gherkin-parser`
2. Steps are matched to implementation functions via `find-glue-by-step-regex`
3. During execution, each step receives the previous step's output state
4. Parameters from the step text are extracted and passed to the implementation
5. Function results and status are captured in the step's `:output-state` and `:status`
6. Scenario status is derived from all contained steps' statuses
7. Feature status is derived from all scenarios' statuses

## Common Transformations

- From Gherkin text → AST via `gherkin-parser`
- From AST → executable feature via `->feature-ast`
- Feature execution via `run-feature`
- Step execution via `run-step`

## Examples Section

For scenarios with examples tables, each row generates a separate execution context:

```clojure
{:scenario-name "Scenario with examples"
 :steps [...]
 :examples [{:header1 "value1", :header2 "value2"},
            {:header1 "value3", :header2 "value4"}]}
```

This data structure provides a flexible representation that preserves all information from the original Gherkin text while supporting execution, reporting, and integration with test frameworks.