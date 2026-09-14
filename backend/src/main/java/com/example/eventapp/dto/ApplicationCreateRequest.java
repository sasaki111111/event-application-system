package com.example.eventapp.dto;

import jakarta.validation.constraints.NotNull;

// 実行環境: サーバー側（JVM）。API-03 POST /api/applications のリクエストボディ。
// userIdはX-User-Idヘッダ（認証情報）から決まるため、ここには含めない（テーブル定義書§7.2）。
public record ApplicationCreateRequest(
        @NotNull(message = "イベントIDを指定してください") Long eventId
) {
}
