package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。API-04 GET /api/my/applications のレスポンス1件分（API設計書§2）。
public record MyApplicationResponse(
        Long id,
        Long eventId,
        String eventName,
        LocalDateTime startAt,
        String status,
        LocalDateTime appliedAt
) {
}
