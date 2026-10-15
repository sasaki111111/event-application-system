package com.example.eventapp.dto;

// 実行環境: サーバー側（JVM）。AP-131（お気に入り総数）・AP-132（コメント総数）の共通レスポンス。
public record CountResponse(long count) {
}
