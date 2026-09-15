package com.example.eventapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// 実行環境: サーバー側（JVM）。POST /api/login（メールアドレスでのログイン、機能追加）のリクエストボディ。
// パスワードは扱わない（ダミー認証の前提は変えない）。
public record LoginRequest(
        @NotBlank(message = "メールアドレスを入力してください")
        @Email(message = "メールアドレスの形式が正しくありません") String email
) {
}
