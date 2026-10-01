package com.example.eventapp.dto;

import java.time.LocalDateTime;
import java.util.List;

// 実行環境: サーバー側（JVM）。機能追加（ソフトデリート）: 管理者の「削除済みイベント」一覧のレスポンス1件分。
// description〜ticketTypesは、削除済みイベント一覧画面（SC-10）からのイベント複製に必要な項目として追加した
// （画面上への表示は必須としない）。
public record DeletedEventResponse(
        Long id,
        String name,
        LocalDateTime startAt,
        String place,
        Integer capacity,
        LocalDateTime deletedAt,
        String description,
        String organizerName,
        String imageUrl,
        String extraQuestion,
        List<TicketTypeResponse> ticketTypes
) {
}
