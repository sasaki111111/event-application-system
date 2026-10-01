package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。AP-12 POST /api/applications のレスポンス（docs/03_API設計書.md AP-12）。
public record ApplicationResponse(
        Long id,
        Long eventId,
        Long ticketTypeId,
        Long userId,
        String status,
        LocalDateTime appliedAt
) {
}
