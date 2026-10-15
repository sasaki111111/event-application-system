package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。AP-050/20のレスポンス（一覧の1要素・投稿結果、docs/30_詳細設計/31_API詳細設計書.md AP-050・AP-051）。
public record EventCommentResponse(
        Long id,
        String userName,
        String body,
        LocalDateTime createdAt,
        // ログイン中ユーザー本人の投稿か（削除ボタンの表示可否に画面側が使う）
        boolean mine,
        // 返信先のコメントID（返信でない場合はNULL）。木構造への組み立てはフロントエンド側が行う
        Long parentCommentId,
        // 返信が残っているため論理削除された（物理削除できなかった）コメントかどうか。
        // trueの場合、bodyは実際の投稿内容ではなく固定の削除済み表示文言になる
        boolean deleted
) {
}
