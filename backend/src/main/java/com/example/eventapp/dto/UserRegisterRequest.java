package com.example.eventapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 実行環境: サーバー側（JVM）。POST /api/users（軽い会員登録、機能追加）のリクエストボディ。
// パスワードは扱わない（ダミー認証の前提は変えない）。
public record UserRegisterRequest(
        @NotBlank(message = "名前を入力してください") @Size(max = 100, message = "100文字以内で入力してください") String name,

        @NotBlank(message = "メールアドレスを入力してください")
        @Email(message = "メールアドレスの形式が正しくありません")
        @Size(max = 255, message = "255文字以内で入力してください") String email
) {
}
