package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。API-20/21のレスポンス（一覧の1要素・投稿結果、API設計書§2）。
public record EventCommentResponse(
        Long id,
        String userName,
        String body,
        LocalDateTime createdAt,
        // ログイン中ユーザー本人の投稿か（削除ボタンの表示可否に画面側が使う）
        boolean mine
) {
}
