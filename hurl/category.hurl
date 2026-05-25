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
### CREATE CATEGORY
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation CreateCategory($request: CreateCategoryRequest!) { createCategory(request: $request) { code message data { id name slug } } }",
  "variables": {
    "request": {
      "name": "Testing Category",
      "slug": "testing-category"
    }
  }
}

HTTP 200

[Captures]
category_id: jsonpath "$.data.createCategory.data.id"


### =====================================
### UPDATE CATEGORY
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation UpdateCategory($id: Long!, $request: UpdateCategoryRequest!) { updateCategory(id: $id, request: $request) { code message data { id name } } }",
  "variables": {
    "id": {{category_id}},
    "request": {
      "name": "Testing Category Updated",
      "slug": "testing-category-updated"
    }
  }
}

HTTP 200


### =====================================
### FIND ALL CATEGORIES
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "query FindAllCategories($request: FindAllCategoryRequest!) { findAllCategories(request: $request) { code message data { id name slug } } }",
  "variables": {
    "request": {
      "page": 1,
      "size": 10,
      "search": "Testing"
    }
  }
}

HTTP 200


### =====================================
### GET CATEGORY BY ID
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "query FindCategoryById($id: Long!) { findCategoryById(id: $id) { code message data { id name } } }",
  "variables": {
    "id": {{category_id}}
  }
}

HTTP 200


### =====================================
### TRASH CATEGORY
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation TrashedCategory($id: Long!) { trashedCategory(id: $id) { code message } }",
  "variables": {
    "id": {{category_id}}
  }
}

HTTP 200


### =====================================
### RESTORE CATEGORY
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation RestoreCategory($id: Long!) { restoreCategory(id: $id) { code message } }",
  "variables": {
    "id": {{category_id}}
  }
}

HTTP 200
