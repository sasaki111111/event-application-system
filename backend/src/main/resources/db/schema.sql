-- C-1: DDL適用（docs/design/テーブル定義書_v1.0.md 準拠）
-- 実行環境: MySQLサーバー（アプリのJVMプロセスとは別）。Spring Bootからは自動実行しない
-- （application.ymlで ddl-auto: none にしているため、このファイルを手動で一度だけ流す）。
--
-- 実行例:
--   mysql -u eventapp_app -p eventapp < backend/src/main/resources/db/schema.sql

-- 3. インデックス一覧・4. 外部キーの都合上、applications → events/users の順に依存するため
-- 作成はusers→events→applicationsの順、削除(DROP)はその逆順で行う。
DROP TABLE IF EXISTS applications;
DROP TABLE IF EXISTS events;
DROP TABLE IF EXISTS users;

-- 2.1 users（利用者）
CREATE TABLE users (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB;

-- 2.2 events（イベント）
CREATE TABLE events (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    name                  VARCHAR(100) NOT NULL,
    start_at              DATETIME     NOT NULL,
    place                 VARCHAR(100) NOT NULL,
    capacity              INT          NOT NULL,
    -- application_deadlineがstart_at以前であることはアプリ側で担保する（DB制約にはしない）
    application_deadline  DATETIME     NOT NULL,
    description           VARCHAR(1000) NULL,
    -- 機能追加（ソフトデリート）: NULL=有効、日時あり=削除済み（管理者の「削除済みイベント」画面から復元可能）
    deleted_at            DATETIME     NULL,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_events_capacity CHECK (capacity >= 1)
) ENGINE = InnoDB;

-- API-01（一覧の開催日時昇順ソート）用インデックス
CREATE INDEX idx_events_start_at ON events (start_at);

-- 2.3 applications（申込）
-- (user_id, event_id)にUNIQUE制約は付けない：キャンセル後の再申込を許すため
-- （二重申込チェックは status='受付済' の行の有無だけをアプリ側で見る。テーブル定義書§2.3の注記）
CREATE TABLE applications (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    event_id   BIGINT      NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT '受付済',
    applied_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_applications_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_applications_event
        FOREIGN KEY (event_id) REFERENCES events (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

-- 定員超過チェック・充足率集計・二重申込チェック用（テーブル定義書§3）
CREATE INDEX idx_app_event_status_user ON applications (event_id, status, user_id);
-- マイページの自分の申込一覧（API-04、申込日時順）用
CREATE INDEX idx_app_user_applied ON applications (user_id, applied_at);
