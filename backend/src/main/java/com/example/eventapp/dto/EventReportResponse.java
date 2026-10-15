package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。AP-130 format=json のレスポンス1件分（docs/30_詳細設計/31_API詳細設計書.md AP-130）。
public record EventReportResponse(
        Long eventId,
        String eventName,
        LocalDateTime startAt,
        Integer capacity,
        long acceptedCount,
        double fillRate
) {
}
