-- C-2: 初期データ投入（要件定義書§2「固定ユーザーは一般1名・管理者1名」に対応）
-- 実行環境: MySQLサーバー。schema.sql適用後に一度だけ実行する（毎起動では実行しない）。
--
-- 実行例:
--   mysql -u eventapp_app -p eventapp < backend/src/main/resources/db/seed.sql
--
-- userId・role（1=general, 2=admin）は
-- backend/.../common/DummyUserStore.java のダミー認証データと一致させている。
-- D以降でDummyUserStoreをRepository参照に置き換える際、ここでの id と一致している必要がある。

-- ロール2種のダミーユーザー
INSERT INTO users (id, name, email, role) VALUES
    (1, '一般ユーザー', 'general@example.com', 'general'),
    (2, '管理者',       'admin@example.com',   'admin');

-- サンプルイベント（開催日時の異なる複数件。1件は申込締切・開催日時とも過去＝「受付終了」表示の確認用）
INSERT INTO events (name, start_at, place, capacity, application_deadline, description) VALUES
    ('新人歓迎ランチ会', '2026-10-01 12:00:00', '本社会議室A', 20,
     '2026-09-25 23:59:59', '新しく入社したメンバーを歓迎するランチ会です。'),
    ('社内勉強会：Spring Boot入門', '2026-10-15 18:00:00', 'オンライン(Teams)', 15,
     '2026-10-10 23:59:59', 'Spring Bootの基礎をハンズオン形式で学びます。'),
    ('秋の社内運動会', '2026-11-05 09:00:00', '市民体育館', 50,
     '2026-10-30 23:59:59', '部署対抗のレクリエーションイベントです。'),
    ('夏祭り懇親会', '2026-08-01 18:00:00', '本社屋上', 30,
     '2026-07-25 23:59:59', '（サンプル：開催日時・申込締切とも終了済みのイベント）');
