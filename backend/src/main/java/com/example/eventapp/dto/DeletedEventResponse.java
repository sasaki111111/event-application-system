package com.example.eventapp.dto;

import java.time.LocalDateTime;
import java.util.List;

// 実行環境: サーバー側（JVM）。AP-123 GET /api/events/deleted（削除済みイベント一覧）のレスポンス1件分
// （docs/30_詳細設計/31_API詳細設計書.md AP-123）。
// description〜ticketTypesは、削除済みイベント画面（SC-122）からのイベント複製に使う項目
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
