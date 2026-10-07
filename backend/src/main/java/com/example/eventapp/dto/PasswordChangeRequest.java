package com.example.eventapp.dto;

import com.example.eventapp.common.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 実行環境: サーバー側（JVM）。AP-012（パスワード変更）のリクエスト。
// 対象の利用者は認証情報から特定するため、利用者IDは含めない（他人のパスワードは変更できない）
public record PasswordChangeRequest(
        @NotBlank(message = "現在のパスワードを入力してください")
        @Size(max = 72, message = "現在のパスワードを入力してください") String currentPassword,
        @NotBlank(message = ValidationPatterns.PASSWORD_MESSAGE)
        @Size(min = 8, max = 72, message = ValidationPatterns.PASSWORD_MESSAGE)
        @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE) String newPassword
) {
}
