package com.example.eventapp.entity;

// 実行環境: サーバー側（JVM）。applications.statusに入る値（要件定義書§4）。
// 文字列を直接あちこちに書かないための定数置き場。
public final class ApplicationStatus {

    public static final String ACCEPTED = "受付済";
    public static final String CANCELLED = "キャンセル済";

    private ApplicationStatus() {
    }
}
