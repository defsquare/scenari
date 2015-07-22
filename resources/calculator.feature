Feature: a calculator engine

  Scenario: addition
    Given I type 2 and 3
    When I press add
    Then the result should be 5