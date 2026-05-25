package com.example.domain.response.api;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@Data
@AllArgsConstructor
public class PagedResult<T> {
    private List<T> data;
    private int totalRecords;
}
