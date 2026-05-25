package com.example.repository.merchantdetail;

import java.util.Optional;
import com.example.domain.response.api.PagedResult;
import com.example.entity.merchant.MerchantDetailsRelation;
import io.smallrye.mutiny.Uni;

public interface MerchantDetailQueryRepositoryCustom {
    Uni<PagedResult<MerchantDetailsRelation>> findAllWithSocialLinks(String keyword, int page, int size);

    Uni<PagedResult<MerchantDetailsRelation>> findActiveWithSocialLinks(String keyword, int page, int size);

    Uni<PagedResult<MerchantDetailsRelation>> findTrashedWithSocialLinks(String keyword, int page, int size);

    Uni<Optional<MerchantDetailsRelation>> findByIdWithSocialLinks(Long merchantDetailId);
}