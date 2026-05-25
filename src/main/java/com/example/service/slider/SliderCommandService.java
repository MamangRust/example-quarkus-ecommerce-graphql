package com.example.service.slider;

import com.example.domain.requests.slider.CreateSliderRequest;
import com.example.domain.requests.slider.UpdateSliderRequest;
import com.example.domain.response.api.ApiResponse;
import com.example.domain.response.slider.SliderResponse;
import com.example.domain.response.slider.SliderResponseDeleteAt;

import io.smallrye.mutiny.Uni;

public interface SliderCommandService {
    Uni<ApiResponse<SliderResponse>> createSlider(CreateSliderRequest req);

    Uni<ApiResponse<SliderResponse>> updateSlider(UpdateSliderRequest req);

    Uni<ApiResponse<SliderResponseDeleteAt>> trashedSlider(Integer sliderId);

    Uni<ApiResponse<SliderResponseDeleteAt>> restoreSlider(Integer sliderId);

    Uni<ApiResponse<Boolean>> deleteSliderPermanent(Integer sliderId);

    Uni<ApiResponse<Boolean>> restoreAllSliders();

    Uni<ApiResponse<Boolean>> deleteAllSlidersPermanent();
}
