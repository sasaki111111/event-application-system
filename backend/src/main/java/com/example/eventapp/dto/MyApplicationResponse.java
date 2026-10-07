package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。AP-031 GET /api/my/applications のレスポンス1件分（docs/30_詳細設計/31_API詳細設計書.md AP-031）。
public record MyApplicationResponse(
        Long id,
        Long eventId,
        String eventName,
        LocalDateTime startAt,
        // 申込状況はコードと表示名の両方を返す（docs/20_基本設計/23_API基本設計書.md 2.8）
        Integer statusCode,
        String statusName,
        LocalDateTime appliedAt,
        // キャンセル待ちの順位（1始まり）。申込状況がキャンセル待ち以外はNULL
        Long waitlistRank
) {
}
