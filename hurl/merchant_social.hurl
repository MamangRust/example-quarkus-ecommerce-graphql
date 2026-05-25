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
### CREATE MERCHANT SOCIAL LINK
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation CreateMerchantSocialLink($request: CreateMerchantSocialRequest!) { createMerchantSocialLink(request: $request) { code message data { id platform url } } }",
  "variables": {
    "request": {
      "merchantId": 1,
      "platform": "INSTAGRAM",
      "url": "https://instagram.com/mymerchant"
    }
  }
}

HTTP 200

[Captures]
social_id: jsonpath "$.data.createMerchantSocialLink.data.id"


### =====================================
### TRASH MERCHANT SOCIAL LINK
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation TrashMerchantSocialLink($id: Int!) { trashMerchantSocialLink(id: $id) { code message } }",
  "variables": {
    "id": {{social_id}}
  }
}

HTTP 200
