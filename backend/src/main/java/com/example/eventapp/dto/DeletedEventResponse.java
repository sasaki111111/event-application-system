package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。機能追加（ソフトデリート）: 管理者の「削除済みイベント」一覧のレスポンス1件分。
public record DeletedEventResponse(
        Long id,
        String name,
        LocalDateTime startAt,
        String place,
        Integer capacity,
        LocalDateTime deletedAt
) {
}
