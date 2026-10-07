package com.example.eventapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。usersテーブル（docs/20_基本設計/22_テーブル定義書.md）に対応するJPAエンティティ。
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

    // パスワードをBCryptでハッシュ化した値（docs/20_基本設計/22_テーブル定義書.md 4.1）。パスワードそのものは保存しない。
    // 退会（匿名化）時はNULLにし、以後ログインできないようにする
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    // 利用者区分コード（RoleCode参照。1=一般利用者、2=管理者）。表示名はコードマスタ（roles）で管理する
    @Column(name = "role_code", nullable = false)
    private Integer roleCode;

    // AP-013: 利用者の匿名化（退会）日時。NULLなら未退会（docs/20_基本設計/22_テーブル定義書.md）
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

    // 利用者登録（AP-011）・管理者アカウント登録（AP-145）用。passwordHashにはハッシュ化済みの値を渡す
    public User(String name, String email, String passwordHash, Integer roleCode) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.roleCode = roleCode;
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

    public String getPasswordHash() {
        return passwordHash;
    }

    // AP-012: パスワード変更。ハッシュ化済みの値で上書きする
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public Integer getRoleCode() {
        return roleCode;
    }

    public boolean isAdmin() {
        return Integer.valueOf(RoleCode.ADMIN).equals(roleCode);
    }

    // AP-146: 管理者権限の降格。利用者区分コードを一般利用者に変更するのみ
    public void demote() {
        this.roleCode = RoleCode.GENERAL;
    }

    public LocalDateTime getAnonymizedAt() {
        return anonymizedAt;
    }

    public boolean isAnonymized() {
        return anonymizedAt != null;
    }

    // AP-013: 利用者の匿名化（退会）。名前・メールアドレスを固定の文言・形式に置き換え、行は残す（物理削除しない）。
    // メールアドレスはUNIQUE制約があるため、利用者IDを用いて他の利用者と重複しない値にする
    public void anonymize() {
        this.name = "退会済み利用者";
        this.email = "withdrawn-" + id + "@invalid.example";
        // パスワードを無効化する（R-24）。以後このアカウントではログインできない
        this.passwordHash = null;
        this.anonymizedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
