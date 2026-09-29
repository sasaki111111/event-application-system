package com.example.eventapp.dto;

import java.time.LocalDateTime;
import java.util.List;

// 実行環境: サーバー側（JVM）。AP-05 GET /api/events/{id} のレスポンス
// （AP-04の全フィールド＋description・remaining等、API設計書§2）。
public record EventDetailResponse(
        Long id,
        String name,
        LocalDateTime startAt,
        String place,
        Integer capacity,
        LocalDateTime applicationDeadline,
        long acceptedCount,
        boolean open,
        String description,
        long remaining,
        String organizerName,
        String imageUrl,
        String extraQuestion,
        List<TicketTypeResponse> ticketTypes,
        // D-14: イベントのお気に入り登録件数。全利用者に返す（管理者限定にはしない）
        long favoriteCount
) {
}
