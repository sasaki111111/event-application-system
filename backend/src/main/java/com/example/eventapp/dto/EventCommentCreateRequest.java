package com.example.eventapp.dto;

import com.example.eventapp.common.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 実行環境: サーバー側（JVM）。AP-20 POST /api/events/{id}/comments のリクエストボディ。
public record EventCommentCreateRequest(
        @NotBlank(message = "コメントを入力してください")
        @Size(max = 500, message = "500文字以内で入力してください")
        @Pattern(regexp = ValidationPatterns.NO_CONTROL_CHARS, message = "使用できない文字が含まれています") String body,
        // 返信先のコメントID。指定した場合は当該コメントへの返信として登録する（任意）
        Long parentCommentId
) {
}
