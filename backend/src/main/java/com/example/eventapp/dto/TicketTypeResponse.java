package com.example.eventapp.dto;

// 実行環境: サーバー側（JVM）。AP-021 イベント詳細・AP-123 削除済みイベント一覧のticketTypes配列1件分のレスポンス
// （docs/30_詳細設計/31_API詳細設計書.md 2.3）。
public record TicketTypeResponse(
        Long id,
        String name,
        Integer capacity,
        long acceptedCount,
        long remaining
) {
}
