package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。AP-125 GET /api/events/{id}/attendees のレスポンス1件分（docs/30_詳細設計/31_API詳細設計書.md AP-125）。
public record AttendeeResponse(
        Long applicationId,
        String userName,
        String ticketTypeName,
        // 申込状況はコードと表示名の両方を返す（docs/20_基本設計/23_API基本設計書.md 2.8）
        Integer statusCode,
        String statusName,
        LocalDateTime checkedInAt,
        // 申込時アンケートへの回答。対象イベントにアンケート設定が無い場合、または未回答の場合はNULL
        String extraAnswer
) {
}
