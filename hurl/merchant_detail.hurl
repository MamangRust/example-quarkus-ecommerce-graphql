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
### CREATE MERCHANT DETAIL
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation CreateMerchantDetail($request: CreateMerchantDetailRequest!) { createMerchantDetail(request: $request) { code message data { id displayName coverImageUrl logoUrl } } }",
  "variables": {
    "request": {
      "merchantId": 1,
      "displayName": "Super Premium Store",
      "coverImageUrl": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=",
      "logoUrl": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=",
      "shortDescription": "The finest store on the platform.",
      "websiteUrl": "https://superpremium.com"
    }
  }
}

HTTP 200

[Captures]
merchant_detail_id: jsonpath "$.data.createMerchantDetail.data.id"


### =====================================
### FIND ALL MERCHANT DETAILS
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "query FindAllMerchantDetails($request: FindAllMerchantRequest!) { findAllMerchantDetails(request: $request) { code message data { id displayName } } }",
  "variables": {
    "request": {
      "page": 1,
      "size": 10
    }
  }
}

HTTP 200


### =====================================
### TRASH MERCHANT DETAIL
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation TrashMerchantDetail($id: Long!) { trashMerchantDetail(id: $id) { code message } }",
  "variables": {
    "id": {{merchant_detail_id}}
  }
}

HTTP 200
