package com.example.eventapp.dto;

import com.example.eventapp.common.validation.ValidationPatterns;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 実行環境: サーバー側（JVM）。AP-12 POST /api/applications のリクエストボディ。
// userIdはX-User-Idヘッダ（認証情報）から決まるため、ここには含めない（docs/03_API設計書.md AP-12）。
public record ApplicationCreateRequest(
        @NotNull(message = "イベントIDを指定してください") Long eventId,

        // 対象イベントに区分が1件以上ある場合は必須（Bean ValidationではなくService層で判定。docs/07_バリデーション設計書.md 8-3 C-03）
        Long ticketTypeId,

        @Size(max = 500, message = "500文字以内で入力してください")
        @Pattern(regexp = ValidationPatterns.NO_CONTROL_CHARS, message = "使用できない文字が含まれています") String extraAnswer
) {
}
