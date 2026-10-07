package com.example.eventapp.dto;

// 実行環境: サーバー側（JVM）。AP-014（ログイン中利用者情報取得）のレスポンス。動作確認用
public record WhoAmIResponse(Long userId, String name, Integer roleCode, String roleName, boolean admin) {
}
