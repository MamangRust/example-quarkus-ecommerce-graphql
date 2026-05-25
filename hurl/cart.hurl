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
### ADD TO CART
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation CreateCart($request: CreateCartRequest!) { createCart(request: $request) { code message data { id userId productId quantity price } } }",
  "variables": {
    "request": {
      "userId": 1,
      "productId": 1,
      "quantity": 3,
      "price": 100000
    }
  }
}

HTTP 200

[Captures]
cart_id: jsonpath "$.data.createCart.data.id"


### =====================================
### FIND ALL CARTS
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "query FindAllCarts($request: FindAllCartsRequest!) { findAllCarts(request: $request) { code message data { id userId productId quantity } } }",
  "variables": {
    "request": {
      "userId": 1,
      "page": 1,
      "size": 10
    }
  }
}

HTTP 200


### =====================================
### DELETE CART ITEM
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation DeleteCart($id: Long!) { deleteCart(id: $id) { code message } }",
  "variables": {
    "id": {{cart_id}}
  }
}

HTTP 200
