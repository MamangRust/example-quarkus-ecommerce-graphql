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
### CREATE MERCHANT POLICY
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation CreateMerchantPolicy($request: CreateMerchantPolicyRequest!) { createMerchantPolicy(request: $request) { code message data { id policyType description } } }",
  "variables": {
    "request": {
      "merchantId": 1,
      "policyType": "RETURN_POLICY",
      "description": "Customers can return products within 30 days of delivery."
    }
  }
}

HTTP 200

[Captures]
policy_id: jsonpath "$.data.createMerchantPolicy.data.id"


### =====================================
### FIND ALL MERCHANT POLICIES
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "query FindAllMerchantPolicies($request: FindAllMerchantRequest!) { findAllMerchantPolicies(request: $request) { code message data { id policyType } } }",
  "variables": {
    "request": {
      "page": 1,
      "size": 10
    }
  }
}

HTTP 200
