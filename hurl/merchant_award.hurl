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
### CREATE MERCHANT AWARD
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation CreateMerchantAward($request: CreateMerchantAwardRequest!) { createMerchantAward(request: $request) { code message data { id name description } } }",
  "variables": {
    "request": {
      "merchantId": 1,
      "name": "Best Seller Award 2026",
      "description": "Award for outstanding customer satisfaction and high sales volume."
    }
  }
}

HTTP 200

[Captures]
award_id: jsonpath "$.data.createMerchantAward.data.id"


### =====================================
### FIND ALL MERCHANT AWARDS
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "query FindAllMerchantAwards($request: FindAllMerchantRequest!) { findAllMerchantAwards(request: $request) { code message data { id name } } }",
  "variables": {
    "request": {
      "page": 1,
      "size": 10
    }
  }
}

HTTP 200
