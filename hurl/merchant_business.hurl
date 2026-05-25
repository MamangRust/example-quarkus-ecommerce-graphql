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
### CREATE MERCHANT BUSINESS
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation CreateMerchantBusiness($request: CreateMerchantBusinessRequest!) { createMerchantBusiness(request: $request) { code message data { id name businessType bankAccountName bankAccountNumber } } }",
  "variables": {
    "request": {
      "merchantId": 1,
      "name": "ECommerce Biz Corp",
      "businessType": "RETAIL",
      "bankAccountName": "ECommerce Biz Store",
      "bankAccountNumber": "9876543210",
      "bankName": "ECommerce Central Bank"
    }
  }
}

HTTP 200

[Captures]
business_id: jsonpath "$.data.createMerchantBusiness.data.id"


### =====================================
### FIND ALL MERCHANT BUSINESSES
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "query FindAllMerchantBusinesses($request: FindAllMerchantRequest!) { findAllMerchantBusinesses(request: $request) { code message data { id name businessType } } }",
  "variables": {
    "request": {
      "page": 1,
      "size": 10
    }
  }
}

HTTP 200
