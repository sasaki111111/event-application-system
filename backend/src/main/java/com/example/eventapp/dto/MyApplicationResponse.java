package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。AP-13 GET /api/my/applications のレスポンス1件分（docs/03_API設計書.md AP-13）。
public record MyApplicationResponse(
        Long id,
        Long eventId,
        String eventName,
        LocalDateTime startAt,
        String status,
        LocalDateTime appliedAt,
        // キャンセル待ちの順位（1始まり）。statusが「キャンセル待ち」以外はNULL
        Long waitlistRank
) {
}
