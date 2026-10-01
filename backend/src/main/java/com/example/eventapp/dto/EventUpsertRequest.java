package com.example.eventapp.dto;

import com.example.eventapp.common.validation.ValidEventDates;
import com.example.eventapp.common.validation.ValidationPatterns;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

// 実行環境: サーバー側（JVM）。イベント登録(AP-07)・編集(AP-08)のリクエストボディ。
// バリデーション内容はAPI設計書 AP-07/08の制約に対応。
@ValidEventDates
public record EventUpsertRequest(
        @NotBlank(message = "名前を入力してください") @Size(max = 100, message = "100文字以内で入力してください")
        @Pattern(regexp = ValidationPatterns.NO_CONTROL_CHARS, message = "使用できない文字が含まれています") String name,

        @NotNull(message = "開催日時を入力してください") @Future(message = "未来の日時を入力してください") LocalDateTime startAt,

        @NotBlank(message = "場所を入力してください") @Size(max = 100, message = "100文字以内で入力してください")
        @Pattern(regexp = ValidationPatterns.NO_CONTROL_CHARS, message = "使用できない文字が含まれています") String place,

        // 要件定義書§8: 参加区分が1件以上ある場合は必須ではない（区分の定員合計で自動算出。EventService.resolveCapacity()参照）。
        // 区分が無い場合は必須（Bean ValidationではなくService層で判定する。ticketTypeIdの要否判定と同じ考え方）
        @Min(value = 1, message = "1以上で入力してください") Integer capacity,

        @NotNull(message = "申込締切を入力してください") LocalDateTime applicationDeadline,

        @Size(max = 1000, message = "1000文字以内で入力してください")
        @Pattern(regexp = ValidationPatterns.NO_CONTROL_CHARS, message = "使用できない文字が含まれています") String description,

        @Size(max = 100, message = "100文字以内で入力してください")
        @Pattern(regexp = ValidationPatterns.NO_CONTROL_CHARS, message = "使用できない文字が含まれています") String organizerName,

        // 画像URLインジェクション対策（docs/07_バリデーション設計書.md 8-3 C-07）: http/https以外のスキーム（javascript:等）を拒否する
        @Size(max = 500, message = "500文字以内で入力してください")
        @Pattern(regexp = ValidationPatterns.HTTP_URL, message = "httpまたはhttpsで始まるURLを入力してください") String imageUrl,

        @Size(max = 200, message = "200文字以内で入力してください")
        @Pattern(regexp = ValidationPatterns.NO_CONTROL_CHARS, message = "使用できない文字が含まれています") String extraQuestion,

        // 0件または未指定＝区分なしイベント。未指定（null）の場合は既存の区分に手を加えない（EventService参照）
        @Valid List<TicketTypeRequest> ticketTypes
) {
}
