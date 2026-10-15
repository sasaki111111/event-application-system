// 実行環境: ブラウザ側。区分値（利用者区分・申込状況）のコードの定数。
// サーバーは区分値をコードと表示名の両方で返す（docs/20_基本設計/23_API基本設計書.md 2.8）。
// 画面は、表示にはAPIが返す表示名（roleName・statusName）をそのまま使い、
// 表示の切り替え（ボタンの表示・非表示等）の判定にはここで定義したコードを使う。
// コードの定義は docs/20_基本設計/22_テーブル定義書.md 7章を正とする。

/** 利用者区分コード（users.role_code）。 */
export const ROLE_CODE = {
  /** 一般利用者 */
  GENERAL: 1,
  /** 管理者 */
  ADMIN: 2,
} as const;

/** 申込状況コード（applications.status_code）。 */
export const STATUS_CODE = {
  /** 受付済 */
  ACCEPTED: 1,
  /** キャンセル待ち */
  WAITLISTED: 2,
  /** キャンセル済 */
  CANCELLED: 9,
} as const;
