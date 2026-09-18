package com.example.eventapp.entity;

import java.util.Set;

// 実行環境: サーバー側（JVM）。applications.statusに入る値（要件定義書§4）。
// 文字列を直接あちこちに書かないための定数置き場。
public final class ApplicationStatus {

    public static final String ACCEPTED = "受付済";
    public static final String CANCELLED = "キャンセル済";
    // 機能追加：キャンセル待ち。定員超過時に登録され、受付済の枠が空くと自動的にACCEPTEDへ繰り上がる
    public static final String WAITLISTED = "キャンセル待ち";

    // 定員・区分の判定上「有効」とみなす状態（受付済＋キャンセル待ち）
    public static final Set<String> ACTIVE_STATUSES = Set.of(ACCEPTED, WAITLISTED);

    private ApplicationStatus() {
    }
}
