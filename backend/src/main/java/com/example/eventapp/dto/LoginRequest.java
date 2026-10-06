package com.example.eventapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// 実行環境: サーバー側（JVM）。POST /api/login（メールアドレスでのログイン、機能追加）のリクエストボディ。
// パスワードは扱わない（ダミー認証の前提は変えない）。
public record LoginRequest(
        @NotBlank(message = "メールアドレスを入力してください")
        @Email(message = "メールアドレスの形式が正しくありません") String email
) {
    // メールアドレスの前後の空白は、形式チェック（@Email）より前に取り除く
    // （docs/07_バリデーション設計書.md 8-6「メールアドレスの正規化」）。
    // recordのコンパクトコンストラクタは、JSONからこのオブジェクトが作られる時点で実行されるため、
    // @Validによる検証は空白を除いた後の値に対して行われる。小文字化はUserService#normalizeEmailが行う
    public LoginRequest {
        email = email == null ? null : email.strip();
    }
}
