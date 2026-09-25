package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。AP-15 PUT /api/applications/{id}/check-in のレスポンス（API設計書§2）。
public record CheckInResponse(
        Long applicationId,
        LocalDateTime checkedInAt
) {
}
