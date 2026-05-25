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
### CREATE ORDER
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation CreateOrder($request: CreateOrderRequest!) { createOrder(request: $request) { code message data { id total amountPaid amountReturned } } }",
  "variables": {
    "request": {
      "userId": 1,
      "merchantId": 1,
      "total": 200000,
      "amountPaid": 250000,
      "amountReturned": 50000,
      "paymentStatus": "SUCCESS",
      "paymentType": "CASH"
    }
  }
}

HTTP 200

[Captures]
order_id: jsonpath "$.data.createOrder.data.id"


### =====================================
### FIND ALL ORDERS
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "query FindAllOrders($request: FindAllOrderRequest!) { findAllOrders(request: $request) { code message data { id total paymentStatus } } }",
  "variables": {
    "request": {
      "page": 1,
      "size": 10
    }
  }
}

HTTP 200


### =====================================
### FIND ORDER BY ID
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "query FindOrderById($id: Long!) { findOrderById(id: $id) { code message data { id total } } }",
  "variables": {
    "id": {{order_id}}
  }
}

HTTP 200


### =====================================
### TRASH ORDER
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation TrashOrder($id: Long!) { trashOrder(id: $id) { code message } }",
  "variables": {
    "id": {{order_id}}
  }
}

HTTP 200
