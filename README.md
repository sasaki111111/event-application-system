# イベント申込システム

チーム開発演習（技術仕様書 v0.1 準拠・1人での教材作成テスト段階）の実装リポジトリ。

## 概要

一般ユーザーが開催中のイベントを検索・申し込み、管理者がイベントの登録・編集・削除と申込実績の確認を行うWebアプリケーション。

## 技術スタック

- フロントエンド：Angular（SPA）
- バックエンド：Spring Boot
- データベース：MySQL
- AI活用：Claude Code

## 設計書

| ドキュメント | 内容 |
|---|---|
| [要件定義書](docs/design/要件定義書_v1.0.md) | システム概要・前提・利用者像・業務シナリオ・機能一覧・業務ルール |
| [画面遷移図](docs/design/画面遷移図_v1.0.md) | 画面一覧・画面遷移 |
| [API設計書](docs/design/API設計書_v1.1.md) | エンドポイント一覧・リクエスト/レスポンス定義 |
| [DB設計書（テーブル定義書）](docs/design/テーブル定義書_v1.0.md) | テーブル定義・ER図・設計書間トレーサビリティ確認 |

その他、演習の技術仕様書・実施要領・Excel版の設計データは [docs/](docs/) 配下を参照。

## セットアップ

### フロントエンド（Angular）

```
cd frontend
npm install
npm start
```

### バックエンド（Spring Boot）

事前にMySQLに `eventapp` データベースと接続用ユーザーを作成しておく（B-4）。

```sql
CREATE DATABASE eventapp CHARACTER SET utf8mb4 COLLATE utf8mb4_ja_0900_as_cs;
CREATE USER 'eventapp_app'@'localhost' IDENTIFIED BY '<任意のパスワード>';
GRANT ALL PRIVILEGES ON eventapp.* TO 'eventapp_app'@'localhost';
```

`backend/src/main/resources/application-local.yml`（gitignore対象・各自作成）に接続情報を書く。

```yaml
spring:
  datasource:
    username: eventapp_app
    password: <上で設定したパスワード>
```

DDL・初期データを投入する（C-1・C-2、`--default-character-set=utf8mb4`を付けないと日本語の既定値でエラーになる）。

```
mysql --default-character-set=utf8mb4 -u eventapp_app -p eventapp < backend/src/main/resources/db/schema.sql
mysql --default-character-set=utf8mb4 -u eventapp_app -p eventapp < backend/src/main/resources/db/seed.sql
```

起動:

```
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

> **コマンドプロンプト（cmd.exe）で実行する場合**：`./mvnw` の `./` はGit Bash／PowerShell向けの書き方で、cmd.exeでは付けずに実行する。
>
> ```
> cd backend
> mvnw spring-boot:run -Dspring-boot.run.profiles=local
> ```
>
> フロントエンド側の `npm install`／`npm start` はシェルを問わずそのまま使える。

起動後、`http://localhost:8080/api/ping` にアクセスして `pong` が返ることを確認できる（B-3の起動確認用エンドポイント）。起動ログにHikariCPの接続完了ログが出ればMySQL接続も確認できている（B-4）。

ダミー認証（B-5）の疎通確認は `http://localhost:8080/api/whoami` に `X-User-Id: 1`（一般ユーザー）または `X-User-Id: 2`（管理者）ヘッダを付けてアクセスする。ヘッダが無い・不正なIDの場合は401が返る。業務APIはD以降で実装する。

## ブランチ運用

- `main`：常に動く状態を保つ。直接コミットしない
- `feature/xxx`：作業ブランチ（1ブランチ＝1タスク）。実装後にPRを作成し、Claude Codeによる差分レビューを経てマージする
