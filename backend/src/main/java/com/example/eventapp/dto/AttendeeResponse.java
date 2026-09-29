package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。AP-11 GET /api/events/{id}/attendees のレスポンス1件分（API設計書§2）。
public record AttendeeResponse(
        Long applicationId,
        String userName,
        String ticketTypeName,
        String status,
        LocalDateTime checkedInAt,
        // D-12: 申込時アンケートへの回答。対象イベントにアンケート設定が無い場合、または未回答の場合はNULL
        String extraAnswer
) {
}
