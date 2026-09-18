package com.example.eventapp.dto;

// 実行環境: サーバー側（JVM）。API-02 イベント詳細のticketTypes配列1件分のレスポンス。
public record TicketTypeResponse(
        Long id,
        String name,
        Integer capacity,
        long acceptedCount,
        long remaining
) {
}
