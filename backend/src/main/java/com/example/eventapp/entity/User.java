package com.example.eventapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。usersテーブル（テーブル定義書_v1.0.md §2.1）に対応するJPAエンティティ。
// DBのCREATE文はbackend/src/main/resources/db/schema.sqlで管理しており、
// このクラスはそこにあわせて手で定義している（ddl-auto: noneのため自動生成はしない）。
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 255, unique = true)
    private String email;

    @Column(nullable = false, length = 20)
    private String role;

    // created_at/updated_atはDB側のDEFAULT/ON UPDATEに任せる（Java側からは書き込まない）。
    // columnDefinitionはテスト環境（H2、ddl-auto: create-drop）でHibernateがスキーマを自動生成する際に
    // 本番のschema.sql同様のDEFAULTを持たせるためのもの（本番はddl-auto: noneのため影響しない）
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    protected User() {
        // JPAが利用するデフォルトコンストラクタ
    }

    // 機能追加（軽い会員登録）: 名前・メールアドレスのみで一般ユーザーを作成する。
    // パスワードは扱わない（要件定義書の前提どおりダミー認証のまま）ため、roleは常にgeneral固定でよい。
    public User(String name, String email, String role) {
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public boolean isAdmin() {
        return "admin".equals(role);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
