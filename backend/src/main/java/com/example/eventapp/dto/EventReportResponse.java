package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。AP-22 format=json のレスポンス1件分（docs/03_API設計書.md AP-22）。
public record EventReportResponse(
        Long eventId,
        String eventName,
        LocalDateTime startAt,
        Integer capacity,
        long acceptedCount,
        double fillRate
) {
}
