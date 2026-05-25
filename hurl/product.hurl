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
### PRE-REQ: CREATE CATEGORY
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation CreateCategory($request: CreateCategoryRequest!) { createCategory(request: $request) { data { id } } }",
  "variables": {
    "request": {
      "name": "Product Category",
      "slug": "product-category"
    }
  }
}

HTTP 200

[Captures]
category_id: jsonpath "$.data.createCategory.data.id"


### =====================================
### CREATE PRODUCT (WITH FILE UPLOAD BASE64 SIMULATION)
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation CreateProduct($request: CreateProductRequest!) { createProduct(request: $request) { code message data { id name price imageProduct } } }",
  "variables": {
    "request": {
      "merchantId": 1,
      "categoryId": {{category_id}},
      "name": "Product A",
      "description": "High quality product description.",
      "price": 99000,
      "countInStock": 10,
      "brand": "MyBrand",
      "weight": 200,
      "rating": 5,
      "slugProduct": "product-a-slug",
      "imageProduct": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA="
    }
  }
}

HTTP 200

[Captures]
product_id: jsonpath "$.data.createProduct.data.id"


### =====================================
### UPDATE PRODUCT
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation UpdateProduct($id: Int!, $request: UpdateProductRequest!) { updateProduct(id: $id, request: $request) { code message data { id name price } } }",
  "variables": {
    "id": {{product_id}},
    "request": {
      "merchantId": 1,
      "categoryId": {{category_id}},
      "name": "Product A Updated",
      "description": "High quality product description updated.",
      "price": 105000,
      "countInStock": 8,
      "brand": "MyBrand",
      "weight": 210,
      "rating": 4,
      "slugProduct": "product-a-slug"
    }
  }
}

HTTP 200


### =====================================
### FIND ALL PRODUCTS
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "query FindAllProducts($request: FindAllProductRequest!) { findAllProducts(request: $request) { code message data { id name price } } }",
  "variables": {
    "request": {
      "page": 1,
      "size": 10,
      "search": "Product"
    }
  }
}

HTTP 200


### =====================================
### GET PRODUCT BY ID
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "query FindProductById($id: Long!) { findProductById(id: $id) { code message data { id name price } } }",
  "variables": {
    "id": {{product_id}}
  }
}

HTTP 200


### =====================================
### TRASH PRODUCT
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation TrashProduct($id: Int!) { trashProduct(id: $id) { code message } }",
  "variables": {
    "id": {{product_id}}
  }
}

HTTP 200


### =====================================
### RESTORE PRODUCT
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation RestoreProduct($id: Int!) { restoreProduct(id: $id) { code message } }",
  "variables": {
    "id": {{product_id}}
  }
}

HTTP 200
