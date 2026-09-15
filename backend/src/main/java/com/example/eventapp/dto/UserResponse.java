package com.example.eventapp.dto;

// 実行環境: サーバー側（JVM）。POST /api/users（軽い会員登録、機能追加）のレスポンス。
// 登録直後にそのままログイン扱いにできるよう、/api/whoamiと同じ形の項目を返す。
public record UserResponse(Long userId, String name, String email, String role) {
}
