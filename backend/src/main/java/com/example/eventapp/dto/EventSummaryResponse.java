package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。AP-020 GET /api/events のレスポンス1件分（docs/30_詳細設計/31_API詳細設計書.md AP-020）。
public record EventSummaryResponse(
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
        // イベントのお気に入り登録件数。全利用者に返す（管理者限定にはしない）
        long favoriteCount
) {
}
