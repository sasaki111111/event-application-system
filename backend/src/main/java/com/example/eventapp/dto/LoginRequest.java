package com.example.eventapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 実行環境: サーバー側（JVM）。POST /api/login（メールアドレスでのログイン、機能追加）のリクエストボディ。
// パスワードは入力されたままの値を受け取る（ハッシュ化・照合はUserServiceが行う）。
public record LoginRequest(
        @NotBlank(message = "メールアドレスを入力してください")
        @Email(message = "メールアドレスの形式が正しくありません") String email,
        // ログイン時は条件（8文字以上等）を検証しない。未入力と上限のみ確認し、照合はUserService#loginが行う
        @NotBlank(message = "パスワードを入力してください")
        @Size(max = 72, message = "パスワードを入力してください") String password
) {
    // メールアドレスの前後の空白は、形式チェック（@Email）より前に取り除く
    // （docs/30_詳細設計/33_共通詳細設計書.md「メールアドレスの正規化」）。
    // recordのコンパクトコンストラクタは、JSONからこのオブジェクトが作られる時点で実行されるため、
    // @Validによる検証は空白を除いた後の値に対して行われる。小文字化はUserService#normalizeEmailが行う
    public LoginRequest {
        email = email == null ? null : email.strip();
    }
}
