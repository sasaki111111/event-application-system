package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。AP-030 POST /api/applications のレスポンス（docs/30_詳細設計/31_API詳細設計書.md AP-030）。
public record ApplicationResponse(
        Long id,
        Long eventId,
        Long ticketTypeId,
        Long userId,
        // 申込状況はコードと表示名の両方を返す（docs/20_基本設計/23_API基本設計書.md 2.8）
        Integer statusCode,
        String statusName,
        LocalDateTime appliedAt
) {
}
