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
### CREATE MERCHANT
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation CreateMerchant($request: CreateMerchantRequest!) { createMerchant(request: $request) { code message data { id name status description } } }",
  "variables": {
    "request": {
      "userId": 1,
      "name": "ECommerce Test Merchant",
      "description": "Premium Merchant Store created via Hurl",
      "address": "123 ECommerce Street",
      "contactEmail": "merchant@ecommerce.com",
      "contactPhone": "+123456789",
      "status": "ACTIVE"
    }
  }
}

HTTP 200

[Captures]
merchant_id: jsonpath "$.data.createMerchant.data.id"


### =====================================
### GET MERCHANT BY ID
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "query FindMerchantById($id: Long!) { findMerchantById(id: $id) { code message data { id name status } } }",
  "variables": {
    "id": {{merchant_id}}
  }
}

HTTP 200


### =====================================
### FIND ALL MERCHANTS
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "query FindAllMerchants($request: FindAllMerchantRequest!) { findAllMerchants(request: $request) { code message data { id name status } } }",
  "variables": {
    "request": {
      "page": 1,
      "size": 10,
      "search": "ECommerce"
    }
  }
}

HTTP 200


### =====================================
### TRASH MERCHANT
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation TrashMerchant($id: Long!) { trashMerchant(id: $id) { code message } }",
  "variables": {
    "id": {{merchant_id}}
  }
}

HTTP 200


### =====================================
### RESTORE MERCHANT
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation RestoreMerchant($id: Long!) { restoreMerchant(id: $id) { code message } }",
  "variables": {
    "id": {{merchant_id}}
  }
}

HTTP 200
