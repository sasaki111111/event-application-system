package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。AP-31 GET /api/users/{id}/comments のレスポンス1件分（D-21）。
// 複数イベントを横断する履歴のため、AP-19（EventCommentResponse）と異なりeventId/eventNameを含む。
public record UserCommentResponse(
        Long id,
        Long eventId,
        String eventName,
        String body,
        LocalDateTime createdAt,
        // 返信が残っているため論理削除（D-18）されたコメントか。trueの場合bodyは固定の削除済み表示文言になる
        boolean deleted
) {
}
