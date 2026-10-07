package com.gesstock.gesstock.articles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategorieRequest(
    @NotBlank @Size(max = 100) String nom,
    @Size(max = 500) String description,
    Boolean active,
    Long parentId
) {}
