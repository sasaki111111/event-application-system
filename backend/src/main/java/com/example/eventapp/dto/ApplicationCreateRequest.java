package com.example.eventapp.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 実行環境: サーバー側（JVM）。API-03 POST /api/applications のリクエストボディ。
// userIdはX-User-Idヘッダ（認証情報）から決まるため、ここには含めない（テーブル定義書§7.2）。
public record ApplicationCreateRequest(
        @NotNull(message = "イベントIDを指定してください") Long eventId,

        // 対象イベントに区分が1件以上ある場合は必須（Bean ValidationではなくService層で判定。詳細設計書_v2.0.md§3.5）
        Long ticketTypeId,

        @Size(max = 500, message = "500文字以内で入力してください") String extraAnswer
) {
}
