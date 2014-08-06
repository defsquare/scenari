feature: product catalog management

Scenario: create a new product
When I create a new product
Then I receive a response with an id
And a location URL
When I invoke a GET request on location URL
Then I receive a 200 response
        Examples:
            |data   |id |title        |description          |
            |product|1  |product title|product description  |

