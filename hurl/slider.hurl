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
### CREATE SLIDER
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation CreateSlider($request: CreateSliderRequest!) { createSlider(request: $request) { code message data { id name image } } }",
  "variables": {
    "request": {
      "nama": "Summer Collection Promo Slider",
      "filePath": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA="
    }
  }
}

HTTP 200

[Captures]
slider_id: jsonpath "$.data.createSlider.data.id"


### =====================================
### FIND ALL SLIDERS
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "query FindAllSliders($request: FindAllSliderRequest!) { findAllSliders(request: $request) { code message data { id name } } }",
  "variables": {
    "request": {
      "page": 1,
      "pageSize": 10,
      "search": "Summer"
    }
  }
}

HTTP 200


### =====================================
### TRASH SLIDER
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation TrashSlider($id: Int!) { trashSlider(id: $id) { code message } }",
  "variables": {
    "id": {{slider_id}}
  }
}

HTTP 200

