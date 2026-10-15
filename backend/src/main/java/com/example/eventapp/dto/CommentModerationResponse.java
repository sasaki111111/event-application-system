package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。AP-150 GET /api/comments のレスポンス1件分（SC-150コメントモデレーション用）。
// 全イベント横断の一覧のため、どのイベント・誰の投稿かがわかるようeventName・userNameを含む。
// 対象は常に有効な（論理削除されていない）コメントのみのため、deletedフラグは持たない。
public record CommentModerationResponse(
        Long id,
        Long eventId,
        String eventName,
        String userName,
        String body,
        LocalDateTime createdAt
) {
}
