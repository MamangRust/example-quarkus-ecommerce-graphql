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
### CREATE BANNER
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation CreateBanner($request: CreateBannerRequest!) { createBanner(request: $request) { code message data { id name imageBanner } } }",
  "variables": {
    "request": {
      "name": "ECommerce Promo Banner",
      "slug": "promo-banner-slug",
      "imageBanner": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA="
    }
  }
}

HTTP 200

[Captures]
banner_id: jsonpath "$.data.createBanner.data.id"


### =====================================
### FIND ALL BANNERS
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "query FindAllBanners($request: FindAllBannerRequest!) { findAllBanners(request: $request) { code message data { id name } } }",
  "variables": {
    "request": {
      "page": 1,
      "size": 10,
      "search": "Promo"
    }
  }
}

HTTP 200


### =====================================
### TRASH BANNER
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation TrashedBanner($id: Long!) { trashedBanner(id: $id) { code message } }",
  "variables": {
    "id": {{banner_id}}
  }
}

HTTP 200
