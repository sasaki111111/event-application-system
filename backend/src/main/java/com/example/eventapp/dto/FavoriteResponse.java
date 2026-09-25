package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。AP-16 POST /api/favorites のレスポンス（API設計書§2）。
public record FavoriteResponse(
        Long id,
        Long eventId,
        LocalDateTime createdAt
) {
}
