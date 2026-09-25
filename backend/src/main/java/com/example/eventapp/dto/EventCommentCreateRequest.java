package com.example.eventapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 実行環境: サーバー側（JVM）。AP-20 POST /api/events/{id}/comments のリクエストボディ。
public record EventCommentCreateRequest(
        @NotBlank(message = "コメントを入力してください")
        @Size(max = 500, message = "500文字以内で入力してください") String body
) {
}
