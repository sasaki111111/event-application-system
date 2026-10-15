package com.example.eventapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 実行環境: サーバー側（JVM）。rolesテーブル（利用者区分マスタ）の1行。コードと表示名の対応を保持する
// （docs/20_基本設計/22_テーブル定義書.md）。登録・変更を行う画面・APIは無く、初期データとして投入する。
@Entity
@Table(name = "roles")
public class RoleMaster {

    // コード値そのものを主キーとする（連番のidは持たない）
    @Id
    @Column(nullable = false)
    private Integer code;

    // 画面・CSVに表示する名称
    @Column(nullable = false, length = 20, unique = true)
    private String name;

    // 一覧・選択肢に表示する際の順序（昇順）
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    protected RoleMaster() {
        // JPAが利用するデフォルトコンストラクタ
    }

    public RoleMaster(Integer code, String name, Integer displayOrder) {
        this.code = code;
        this.name = name;
        this.displayOrder = displayOrder;
    }

    public Integer getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }
}
