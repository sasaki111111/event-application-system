package com.example.eventapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。usersテーブル（docs/02_テーブル定義書.md §4.1）に対応するJPAエンティティ。
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

    // AP-34: 利用者の匿名化（退会）日時。NULLなら未退会（docs/02_テーブル定義書.md §4.1）
    @Column(name = "anonymized_at")
    private LocalDateTime anonymizedAt;

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

    // AP-33: 管理者権限の降格。roleをgeneralに変更するのみ（新たな列は追加しない）
    public void demote() {
        this.role = "general";
    }

    public LocalDateTime getAnonymizedAt() {
        return anonymizedAt;
    }

    public boolean isAnonymized() {
        return anonymizedAt != null;
    }

    // AP-34: 利用者の匿名化（退会）。名前・メールアドレスを固定の文言・形式に置き換え、行は残す（物理削除しない）。
    // メールアドレスはUNIQUE制約があるため、利用者IDを用いて他の利用者と重複しない値にする
    public void anonymize() {
        this.name = "退会済み利用者";
        this.email = "withdrawn-" + id + "@invalid.example";
        this.anonymizedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
