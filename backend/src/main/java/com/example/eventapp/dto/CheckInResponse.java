package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。API-19 PUT /api/applications/{id}/check-in のレスポンス（API設計書§2）。
public record CheckInResponse(
        Long applicationId,
        LocalDateTime checkedInAt
) {
}
