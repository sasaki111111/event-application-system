package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。AP-040 POST /api/favorites のレスポンス（docs/30_詳細設計/31_API詳細設計書.md AP-040）。
public record FavoriteResponse(
        Long id,
        Long eventId,
        LocalDateTime createdAt
) {
}
