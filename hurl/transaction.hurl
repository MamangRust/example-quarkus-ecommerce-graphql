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
### CREATE TRANSACTION
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation CreateTransaction($request: CreateTransactionRequest!) { createTransaction(request: $request) { code message data { id amount status paymentMethod } } }",
  "variables": {
    "request": {
      "orderId": 1,
      "amount": 250000,
      "status": "SUCCESS",
      "paymentMethod": "CASH",
      "transactionTime": "2026-05-24T12:00:00"
    }
  }
}

HTTP 200

[Captures]
transaction_id: jsonpath "$.data.createTransaction.data.id"


### =====================================
### FIND ALL TRANSACTIONS
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "query FindAllTransactions($request: FindAllTransactionRequest!) { findAllTransactions(request: $request) { code message data { id amount status } } }",
  "variables": {
    "request": {
      "page": 1,
      "size": 10
    }
  }
}

HTTP 200


### =====================================
### FIND TRANSACTION BY ID
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "query FindTransactionById($id: Int!) { findTransactionById(id: $id) { code message data { id amount status } } }",
  "variables": {
    "id": {{transaction_id}}
  }
}

HTTP 200


### =====================================
### TRASH TRANSACTION
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation TrashTransaction($id: Int!) { trashTransaction(id: $id) { code message } }",
  "variables": {
    "id": {{transaction_id}}
  }
}

HTTP 200
