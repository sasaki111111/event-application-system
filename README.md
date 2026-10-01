# イベント申込システム

チーム開発演習（技術仕様書 v0.1 準拠・1人での教材作成テスト段階）の実装リポジトリ。

## 概要

一般ユーザーが開催中のイベントを一覧・カレンダー・キーワード検索で探し、お気に入り登録やコメント投稿（返信を含む）を行いながら参加を申し込む（参加区分・申込時アンケートへの回答に対応、定員超過時は自動的にキャンセル待ちとなり、キャンセル発生時は自動繰り上げ）。管理者はイベントの登録・編集・複製・削除（削除済みからの復元可）、当日受付（チェックイン、申込時アンケート回答の確認を含む）、申込実績の確認・CSV出力、利用者一覧・利用者詳細（申込・お気に入り・コメント履歴）の確認、全コメント横断のモデレーション、管理者アカウントの追加登録を行う。パスワードは使わず、メールアドレスのみで識別するダミー認証（学内利用を前提）で動作するWebアプリケーション。

## 技術スタック

- フロントエンド：Angular（SPA）
- バックエンド：Spring Boot
- データベース：MySQL
- AI活用：Claude Code

## 設計書

現在の正式な基準となる設計・定義書は次の12点（`docs/01`〜`12`）。実装・DB・画面はこれらの内容に一致させている。

| ドキュメント | 内容 |
|---|---|
| [01_要件定義書.md](docs/01_要件定義書.md) | システム概要・機能一覧・機能要件・業務ルール・入力/エラー要件・非機能要件 |
| [02_テーブル定義書.md](docs/02_テーブル定義書.md) | DBのテーブル定義（ER図・カラム定義・リレーション） |
| [03_API設計書.md](docs/03_API設計書.md) | API仕様（計32 API） |
| [04_画面設計書.md](docs/04_画面設計書.md) | 画面ごとの表示項目・入力項目・操作・対応API |
| [05_画面遷移図.md](docs/05_画面遷移図.md) | 画面遷移の全体像・アクセス制御 |
| [06_詳細設計書.md](docs/06_詳細設計書.md) | Controller/Service/Repository等の内部設計 |
| [07_バリデーション設計書.md](docs/07_バリデーション設計書.md) | 入力項目ごとのバリデーションルール |
| [08_エラー設計書.md](docs/08_エラー設計書.md) | エラーコード・エラーメッセージ・例外ハンドリング方針 |
| [09_認証認可設計書.md](docs/09_認証認可設計書.md) | ダミー認証・権限チェックの設計 |
| [10_非機能設計書.md](docs/10_非機能設計書.md) | 性能・ログ・運用等の非機能要件への対応方針 |
| [11_ログ設計書.md](docs/11_ログ設計書.md) | ログ出力方針・出力項目 |
| [12_設計確定時の確認事項.md](docs/12_設計確定時の確認事項.md) | 上記11点を正式確定させる過程での決定事項・未確定事項の記録 |

コードを読む際の補助資料として [docs/コードベース解説.md](docs/コードベース解説.md)（フォルダ・ファイル単位の役割まとめ）もある。設計とコードの対応表・確認表一式は `docs/13-01`〜`13-06`（[13_システム動作・実装対応チェックリスト.md](docs/13_システム動作・実装対応チェックリスト.md)から参照）、追加開発のWBSは `docs/開発演習_WBS.xlsx` を参照。

## セットアップ

### 事前に必要なソフト

| ソフト | バージョンの目安 |
|---|---|
| Java（JDK） | 21 |
| Node.js | 20系以降 |
| MySQL Server | 8.0 |
| Git | コードを取得する場合 |

別のPCで動かす場合は、まずこのリポジトリを取得する。

```
git clone https://github.com/sasaki111111/event-application-system.git
```

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

ダミー認証（B-5）の疎通確認は `http://localhost:8080/api/whoami` に `X-User-Id: 1`（一般ユーザー）または `X-User-Id: 2`（管理者）ヘッダを付けてアクセスする。ヘッダが無い・不正なIDの場合は401が返る。

起動後、`http://localhost:8080/swagger-ui.html` からAPIドキュメント（Swagger UI）を確認できる。認証不要な3 APIを除く全APIに`X-User-Id`ヘッダの入力欄が自動で追加されているので、一般ユーザーを試す場合は`1`、管理者を試す場合は`2`を指定すればSwagger UI単体で動作確認できる。

### 使い方

フロントエンド・バックエンドの両方を起動した状態で、ブラウザで `http://localhost:4200` を開く（`http://localhost:8080` はバックエンドのAPIのみで画面は無い）。

ログインはパスワード無し、メールアドレスのみ。

- 一般ユーザー：`general@example.com`
- 管理者：`admin@example.com`

未登録のメールアドレスの場合は、ログイン画面の新規登録欄から名前・メールアドレスのみで一般ユーザーを作成できる（作成されるのは常に一般ユーザー）。管理者アカウントを追加したい場合は、管理者でログインし「利用者管理」画面から登録する。

## ブランチ運用

- `main`：常に動く状態を保つ。直接コミットしない
- `feature/xxx`：作業ブランチ（1ブランチ＝1タスク）。実装後にPRを作成し、Claude Codeによる差分レビューを経てマージする
