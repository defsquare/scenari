Narrative:
As a user
I want to login
In order to access to protected resource

Scenario: successful authentication with remember-me worflow and remember-me checkbox unchecked
Given a running webapp and a user storage containing {:username "john" :password "test-password"} and no remember-me token
When the user login (POST to "/login" URL with form params username=john and password=test-password)
Then the user should be authenticated (http response 200 and welcome page is displayed)

Scenario: failed authentication with remember-me workflow and no remember-me checkbox unchecked
Given a running webapp and a user storage containing {:username "john" :password "test-password"} and no remember-me token
When the user login (POST to "/login" URL with form params username=john and password=incorrect)
Then the user should not be authenticated (http response 403 and login page still displayed with error message)

Scenario: successful authentication with remember-me worflow and remember-me checkbox checked
Given a running webapp and a user storage containing {:username "john" :password "test-password"} and no remember-me token
When the user login (POST to "/login" URL with form params username=john and password=test-password and remember-me=true)
Then the user should be authenticated (http response 200 and welcome page is displayed)
Then the http response should contain a cookie named "remember-me"

Scenario: successful authentication with remember-me token
Given the user session expires on the server
When the user perform an http request on a protected resource (welcome page) with the token cookie
Then the user should be authenticated (http response 200 and welcome page displayed)
