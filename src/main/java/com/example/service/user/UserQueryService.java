package com.example.service.user;

import java.util.List;

import com.example.domain.requests.user.FindAllUsers;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.user.UserResponse;
import com.example.domain.response.user.UserResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface UserQueryService {
    Uni<ApiResponsePagination<List<UserResponse>>> findAllPaginated(FindAllUsers request);

    Uni<ApiResponsePagination<List<UserResponseDeleteAt>>> findActivePaginated(FindAllUsers request);

    Uni<ApiResponsePagination<List<UserResponseDeleteAt>>> findTrashedPaginated(FindAllUsers request);

    Uni<ApiResponse<UserResponse>> findById(Long id);
}
