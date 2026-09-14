package com.example.eventapp.dto;

import com.example.eventapp.common.validation.ValidEventDates;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。イベント登録(API-06)・編集(API-07)のリクエストボディ。
// バリデーション内容はAPI設計書 API-06/07の制約に対応。
@ValidEventDates
public record EventUpsertRequest(
        @NotBlank(message = "名前を入力してください") @Size(max = 100, message = "100文字以内で入力してください") String name,

        @NotNull(message = "開催日時を入力してください") @Future(message = "未来の日時を入力してください") LocalDateTime startAt,

        @NotBlank(message = "場所を入力してください") @Size(max = 100, message = "100文字以内で入力してください") String place,

        @NotNull(message = "定員を入力してください") @Min(value = 1, message = "1以上で入力してください") Integer capacity,

        @NotNull(message = "申込締切を入力してください") LocalDateTime applicationDeadline,

        @Size(max = 1000, message = "1000文字以内で入力してください") String description
) {
}
