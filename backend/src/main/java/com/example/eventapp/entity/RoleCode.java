package com.example.eventapp.entity;

// 実行環境: サーバー側（JVM）。users.role_codeに入るコード値（docs/20_基本設計/22_テーブル定義書.md 7章）。
// 表示名（一般利用者・管理者）はコードマスタ（roles）で管理し、ここには持たない。
public final class RoleCode {

    // 一般利用者
    public static final int GENERAL = 1;

    // 管理者
    public static final int ADMIN = 2;

    private RoleCode() {
    }
}
