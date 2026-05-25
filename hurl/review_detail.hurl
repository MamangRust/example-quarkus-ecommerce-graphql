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
### CREATE REVIEW DETAILS
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation CreateReviewDetails($requests: [CreateReviewDetailRequest!]!) { createReviewDetails(requests: $requests) { code message data { id reviewId type url caption } } }",
  "variables": {
    "requests": [
      {
        "reviewId": 1,
        "type": "image",
        "file": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=",
        "caption": "The item matches descriptions exactly. High resolution stitching."
      },
      {
        "reviewId": 1,
        "type": "image",
        "file": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=",
        "caption": "Shipped extremely fast and packed securely."
      }
    ]
  }
}

HTTP 200

[Captures]
detail_id: jsonpath "$.data.createReviewDetails.data[0].id"


### =====================================
### TRASH REVIEW DETAIL
### =====================================
POST http://localhost:8080/graphql
Content-Type: application/json
Authorization: Bearer {{access_token}}

{
  "query": "mutation TrashReviewDetail($id: Int!) { trashReviewDetail(id: $id) { code message } }",
  "variables": {
    "id": {{detail_id}}
  }
}

HTTP 200

