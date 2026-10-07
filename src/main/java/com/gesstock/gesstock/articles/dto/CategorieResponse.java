package com.gesstock.gesstock.articles.dto;

public record CategorieResponse(
    Long id,
    String nom,
    String description,
    boolean active,
    Long parentId
) {}
