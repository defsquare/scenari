Feature: foo bar kix

  Scenario: create a new product
    When I invoke a GET request on location URL
     # this is a comment
    When I create a new product with name "iphone 6" and description "awesome phone" with properties
      | size | weight |
      | 6    | 2      |
    Then I receive a response with an id 56422
    Then a location URL
  Scenario: another
    Given I foo

