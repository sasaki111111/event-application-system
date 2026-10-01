package com.example.eventapp.dto;

import com.example.eventapp.common.validation.ValidationPatterns;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 実行環境: サーバー側（JVM）。イベント登録・編集(AP-07/08)のticketTypes配列1件分のリクエスト。
public record TicketTypeRequest(
        @NotBlank(message = "区分名を入力してください") @Size(max = 50, message = "50文字以内で入力してください")
        @Pattern(regexp = ValidationPatterns.NO_CONTROL_CHARS, message = "使用できない文字が含まれています") String name,

        @NotNull(message = "区分の定員を入力してください") @Min(value = 1, message = "1以上で入力してください") Integer capacity
) {
}
