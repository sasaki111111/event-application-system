package com.example.eventapp.dto;

import jakarta.validation.constraints.NotNull;

// 実行環境: サーバー側（JVM）。API-15 POST /api/favorites のリクエストボディ。
public record FavoriteCreateRequest(
        @NotNull(message = "イベントIDを指定してください") Long eventId
) {
}
