package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。AP-042 GET /api/my/favorites のレスポンス1件分
// （AP-020(EventSummaryResponse)と同じ項目＋favoritedAt、docs/30_詳細設計/31_API詳細設計書.md AP-042）。
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
        LocalDateTime favoritedAt
) {
}
