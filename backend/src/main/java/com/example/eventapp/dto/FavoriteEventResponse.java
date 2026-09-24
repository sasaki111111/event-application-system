package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。API-17 GET /api/my/favorites のレスポンス1件分
// （API-01(EventSummaryResponse)と同じ項目＋favoritedAt、API設計書§2）。
public record FavoriteEventResponse(
        Long id,
        String name,
        LocalDateTime startAt,
        String place,
        Integer capacity,
        LocalDateTime applicationDeadline,
        long acceptedCount,
        boolean open,
        String organizerName,
        String imageUrl,
        String category,
        LocalDateTime favoritedAt
) {
}
