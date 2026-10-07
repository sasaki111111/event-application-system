package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。AP-126 PUT /api/applications/{id}/check-in のレスポンス（docs/30_詳細設計/31_API詳細設計書.md AP-126）。
public record CheckInResponse(
        Long applicationId,
        LocalDateTime checkedInAt
) {
}
