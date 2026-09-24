package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。API-18 GET /api/events/{id}/attendees のレスポンス1件分（API設計書§2）。
public record AttendeeResponse(
        Long applicationId,
        String userName,
        String ticketTypeName,
        String status,
        LocalDateTime checkedInAt
) {
}
