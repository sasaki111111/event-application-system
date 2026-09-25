-- C-1: DDL適用（docs/design/テーブル定義書_v2.0.md 準拠）
-- 実行環境: MySQLサーバー（アプリのJVMプロセスとは別）。Spring Bootからは自動実行しない
-- （application.ymlで ddl-auto: none にしているため、このファイルを手動で一度だけ流す）。
--
-- 実行例:
--   mysql -u eventapp_app -p eventapp < backend/src/main/resources/db/schema.sql

-- 外部キーの都合上、依存される側から先に作成し、削除(DROP)は依存する側から先に行う
-- （drop順はcreate順を厳密に逆転させたものである必要はなく、「子→親」の順を守っていればよい）。
-- 作成順: users → events → favorites → ticket_types → event_comments → applications
DROP TABLE IF EXISTS applications;
DROP TABLE IF EXISTS event_comments;
DROP TABLE IF EXISTS favorites;
DROP TABLE IF EXISTS ticket_types;
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
    -- 区分（ticket_types）が無いイベントでは定員そのもの。区分がある場合は区分の定員合計をアプリ側で同期する参考値
    capacity              INT          NOT NULL,
    -- application_deadlineがstart_at以前であることはアプリ側で担保する（DB制約にはしない）
    application_deadline  DATETIME     NOT NULL,
    description           VARCHAR(1000) NULL,
    -- ソフトデリート: NULL=有効、日時あり=削除済み（管理者の「削除済みイベント」画面から復元可能）
    deleted_at            DATETIME     NULL,
    organizer_name        VARCHAR(100) NULL,
    image_url             VARCHAR(500) NULL,
    -- 申込時アンケートの質問文言。NULL＝アンケート無し
    extra_question        VARCHAR(200) NULL,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_events_capacity CHECK (capacity >= 1)
) ENGINE = InnoDB;

-- API-01（一覧の開催日時昇順ソート）用インデックス
CREATE INDEX idx_events_start_at ON events (start_at);

-- 2.4 favorites（お気に入り）
CREATE TABLE favorites (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    event_id   BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 同じ組み合わせの二重登録を防ぐ（重複時はアプリ側で冪等に処理する）
    CONSTRAINT uk_favorites_user_event UNIQUE (user_id, event_id),
    CONSTRAINT fk_favorites_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_favorites_event
        FOREIGN KEY (event_id) REFERENCES events (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

-- 2.5 ticket_types（定員区分／チケット種別）
-- event_id列にはfk_ticket_types_eventの作成時にInnoDBが自動でインデックスを張るため、
-- 別途CREATE INDEXは行わない（同一列への重複インデックスを避けるため）
CREATE TABLE ticket_types (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    event_id   BIGINT       NOT NULL,
    name       VARCHAR(50)  NOT NULL,
    capacity   INT          NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_ticket_types_capacity CHECK (capacity >= 1),
    -- 同一イベント内での区分名の重複を禁止する（テーブル定義書§4.3、D-07）
    CONSTRAINT uk_ticket_types_event_name UNIQUE (event_id, name),
    CONSTRAINT fk_ticket_types_event
        FOREIGN KEY (event_id) REFERENCES events (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

-- 2.6 event_comments（イベントコメント）
CREATE TABLE event_comments (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    event_id   BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    body       VARCHAR(500) NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_event_comments_event
        FOREIGN KEY (event_id) REFERENCES events (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_event_comments_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE INDEX idx_event_comments_event_created ON event_comments (event_id, created_at);

-- 2.3 applications（申込）
-- (user_id, event_id)にUNIQUE制約は付けない：キャンセル後の再申込を許すため
-- （二重申込チェックは status='受付済' または 'キャンセル待ち' の行の有無だけをアプリ側で見る）
CREATE TABLE applications (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         BIGINT      NOT NULL,
    event_id        BIGINT      NOT NULL,
    -- 申し込んだ区分。対象イベントに区分が無い場合はNULL
    ticket_type_id  BIGINT      NULL,
    status          VARCHAR(20) NOT NULL DEFAULT '受付済',
    applied_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 申込時アンケートの回答。対象イベントにextra_questionが無い場合はNULL
    extra_answer    VARCHAR(500) NULL,
    -- 当日受付でチェックインされた日時。NULL＝未チェックイン
    checked_in_at   DATETIME    NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_applications_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_applications_event
        FOREIGN KEY (event_id) REFERENCES events (id)
        ON DELETE CASCADE,
    -- RESTRICTは「申込が残っている区分は削除できない」という業務ルール（テーブル定義書§4）のため。
    -- events→ticket_types・events→applicationsは共にCASCADEだが、物理削除はAPI経由では発生しない
    -- （イベント削除はソフトデリートのみ。EventService.delete()参照）ため、CASCADE同士の処理順序に
    -- InnoDBの保証が無い点（子テーブル間の処理順は未規定）がこのRESTRICTと衝突することはない。
    CONSTRAINT fk_applications_ticket_type
        FOREIGN KEY (ticket_type_id) REFERENCES ticket_types (id)
        ON DELETE RESTRICT
) ENGINE = InnoDB;

-- 定員超過チェック・充足率集計・二重申込チェック用（テーブル定義書§3）
CREATE INDEX idx_app_event_status_user ON applications (event_id, status, user_id);
-- マイページの自分の申込一覧（API-04、申込日時順）用
CREATE INDEX idx_app_user_applied ON applications (user_id, applied_at);
-- 区分単位の定員超過チェック・受付済数集計用
CREATE INDEX idx_app_ticket_type_status ON applications (ticket_type_id, status);
