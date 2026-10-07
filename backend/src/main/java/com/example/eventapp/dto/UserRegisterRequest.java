package com.example.eventapp.dto;

import com.example.eventapp.common.validation.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 実行環境: サーバー側（JVM）。POST /api/users（軽い会員登録、機能追加）のリクエストボディ。
// パスワードは入力されたままの値を受け取る（ハッシュ化・照合はUserServiceが行う）。
public record UserRegisterRequest(
        @NotBlank(message = "名前を入力してください") @Size(max = 100, message = "100文字以内で入力してください")
        @Pattern(regexp = ValidationPatterns.NO_CONTROL_CHARS, message = "使用できない文字が含まれています") String name,

        @NotBlank(message = "メールアドレスを入力してください")
        @Email(message = "メールアドレスの形式が正しくありません")
        @Size(max = 255, message = "255文字以内で入力してください") String email,
        // パスワードの条件: 8〜72文字、英字と数字を各1文字以上（docs/20_基本設計/24_方式設計書.md 3.1）。
        // ハッシュ化はUserServiceが行う（このDTOは入力されたパスワードをそのまま保持する）
        @NotBlank(message = ValidationPatterns.PASSWORD_MESSAGE)
        @Size(min = 8, max = 72, message = ValidationPatterns.PASSWORD_MESSAGE)
        @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE) String password
) {
    // メールアドレスの前後の空白は、形式チェック（@Email）より前に取り除く
    // （docs/30_詳細設計/33_共通詳細設計書.md「メールアドレスの正規化」）。
    // recordのコンパクトコンストラクタは、JSONからこのオブジェクトが作られる時点で実行されるため、
    // 利用者登録（@Valid）・管理者アカウント登録（Controller内の手動検証）のどちらでも、
    // 検証は空白を除いた後の値に対して行われる。小文字化はUserService#normalizeEmailが行う
    public UserRegisterRequest {
        email = email == null ? null : email.strip();
    }
}
