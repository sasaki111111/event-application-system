package com.example.eventapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * DDL（db/schema.sql）と初期データ（db/seed.sql）が、エラー無く順に実行できることを確認するテスト。
 * 他のテストはエンティティ定義からテーブルを自動生成する（ddl-auto: create-drop）ため、schema.sql・seed.sqlの
 * 記述誤り（カンマの抜け、カラム名の不一致、外部キーの参照先の誤り等）を検出できない。そこで、テスト専用の
 * インメモリDB（H2のMySQL互換モード）に対してスクリプトそのものを実行する。
 * H2とMySQLには方言の差があるため、MySQLでの実行を完全に保証するものではない。
 */
// 実行環境: サーバー側（JVM）。Springのコンテキストは起動せず、JDBCで直接H2に接続する。
class SchemaScriptTest {

    @Test
    void schemaとseedを順に実行でき初期データが投入される() throws Exception {
        // 他のテストのDB（testdb）とは別の、このテスト専用のインメモリDBを使う
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:schemacheck;MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "")) {
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new ClassPathResource("db/schema.sql"), "UTF-8"));
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new ClassPathResource("db/seed.sql"), "UTF-8"));

            try (Statement statement = connection.createStatement()) {
                // コードマスタ: 利用者区分2件、申込状況3件（docs/20_基本設計/22_テーブル定義書.md 7章）
                assertThat(count(statement, "SELECT COUNT(*) FROM roles")).isEqualTo(2);
                assertThat(count(statement, "SELECT COUNT(*) FROM application_statuses")).isEqualTo(3);
                // 初期利用者3件。全員がコードマスタに存在する利用者区分を持つ
                assertThat(count(statement, "SELECT COUNT(*) FROM users")).isEqualTo(3);
                assertThat(count(statement,
                        "SELECT COUNT(*) FROM users u JOIN roles r ON r.code = u.role_code")).isEqualTo(3);
                // 管理者（id=2）の利用者区分コードが2（管理者）であること
                assertThat(count(statement, "SELECT role_code FROM users WHERE id = 2")).isEqualTo(2);
                // サンプルイベントが投入されていること
                assertThat(count(statement, "SELECT COUNT(*) FROM events")).isPositive();

                // 初期利用者のパスワードハッシュが、初期パスワード（Test1234）と照合できること
                try (ResultSet rs = statement.executeQuery("SELECT password_hash FROM users ORDER BY id")) {
                    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
                    while (rs.next()) {
                        assertThat(encoder.matches("Test1234", rs.getString(1))).isTrue();
                    }
                }
            }
        }
    }

    private int count(Statement statement, String sql) throws Exception {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
