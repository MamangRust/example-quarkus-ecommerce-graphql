### =====================================
### PRE-REQ: LOGIN
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json

{
  "query": "mutation Login($input: LoginRequest!) { login(input: $input) { accessToken } }",
  "variables": {
    "input": {
      "username": "hurl_test_user",
      "password": "Password123!"
    }
  }
}

HTTP 200

[Captures]
access_token: jsonpath "$.data.login.accessToken"


### =====================================
### CREATE REVIEW
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation CreateReview($request: CreateReviewRequest!) { createReview(request: $request) { code message data { id rating comment } } }",
  "variables": {
    "request": {
      "userId": 1,
      "productId": 1,
      "rating": 5,
      "comment": "Incredible e-commerce product! Highly recommended!"
    }
  }
}

HTTP 200

[Captures]
review_id: jsonpath "$.data.createReview.data.id"


### =====================================
### FIND ALL REVIEWS
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "query FindAllReviews($request: FindAllReview!) { findAllReviews(request: $request) { code message data { id rating comment } } }",
  "variables": {
    "request": {
      "page": 1,
      "size": 10
    }
  }
}

HTTP 200
