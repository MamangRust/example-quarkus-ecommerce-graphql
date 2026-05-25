package com.example.service.slider;

import java.util.List;

import com.example.domain.requests.slider.FindAllSliderRequest;
import com.example.domain.response.api.ApiResponsePagination;
import com.example.domain.response.slider.SliderResponse;
import com.example.domain.response.slider.SliderResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface SliderQueryService {
    Uni<ApiResponsePagination<List<SliderResponse>>> findAll(FindAllSliderRequest req);

    Uni<ApiResponsePagination<List<SliderResponseDeleteAt>>> findByActive(FindAllSliderRequest req);

    Uni<ApiResponsePagination<List<SliderResponseDeleteAt>>> findByTrashed(FindAllSliderRequest req);
}
