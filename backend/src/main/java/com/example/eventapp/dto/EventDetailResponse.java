package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。API-02 GET /api/events/{id} のレスポンス
// （API-01の全フィールド＋description・remaining、API設計書§2）。
public record EventDetailResponse(
        Long id,
        String name,
        LocalDateTime startAt,
        String place,
        Integer capacity,
        LocalDateTime applicationDeadline,
        long acceptedCount,
        boolean open,
        String description,
        long remaining
) {
}
