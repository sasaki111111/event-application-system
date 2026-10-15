// 実行環境: ブラウザ側。ログイン中のユーザーを保持する入れ物（E-2）。
// パスワード認証は実装対象外（要件定義書）のため、ログイン＝ロール選択のみ。未ログイン時はnull。
// 管理者判定はバックエンドが返すrole（"admin"/"general"）に基づいて行う（userIdの決め打ちはしない）。
//
// [Angularの基礎: signal()とcomputed()] signal()は、値が変わると参照している画面（テンプレート）
// を自動的に再描画してくれる「状態を持つ箱」。値を読むときは関数のように signal() の形で呼び、
// 書き込むときは .set(newValue) を使う。computed()は、他のsignalから導き出す「読み取り専用の
// 派生値」を作る関数で、元のsignalが変わると自動的に再計算される。
// このクラスはサービス（@Injectable、providedIn: 'root'）としてアプリ全体で1つだけ生成され、
// ログイン中ユーザーの状態を画面間（ログイン画面・共通ナビ・各ガード等）で共有するために使われる。
// Injectable: サービスとして登録するデコレーター。computed/signal: 状態管理の仕組み（上記参照）
import { Injectable, computed, signal } from '@angular/core';
import { ROLE_CODE } from './codes';

const USER_ID_STORAGE_KEY = 'loginUserId';
const ROLE_STORAGE_KEY = 'loginUserRoleCode';

/**
 * ログイン中ユーザーのuserId・roleを保持し、画面間で共有するストア。
 * login.ts（ログイン・登録成功時）、app.ts（共通ナビの表示・ログアウト）、
 * auth-guard.ts／admin-guard.ts（遷移可否の判定）、auth-header-interceptor.ts
 * （リクエストヘッダへの付与）から利用される。ページ再読み込みをまたいで状態を保てるよう、
 * localStorageにも保存する。
 */
@Injectable({ providedIn: 'root' })
export class LoginUserStore {
  // 初期値はlocalStorageから読み出す（無ければnull＝未ログイン）
  private readonly userId = signal(this.readInitial(USER_ID_STORAGE_KEY));
  private readonly role = signal(this.readInitial(ROLE_STORAGE_KEY));

  // asReadonly(): 外部からは値を読むことだけ許し、.set()等での書き換えはこのクラスの中
  // （login/logoutメソッド）からしかできないようにする
  readonly currentUserId = this.userId.asReadonly();
  readonly currentRole = this.role.asReadonly();
  // roleが"admin"文字列かどうかを毎回比較し、boolean値として返す派生値
  readonly isAdmin = computed(() => this.role() === String(ROLE_CODE.ADMIN));

  /** ログイン状態にする。login.tsのログイン成功・会員登録成功時に呼ばれる。 */
  login(id: string, roleCode: number): void {
    // signalの値を更新する。これによりcurrentUserId・currentRole・isAdminを参照している
    // テンプレート（app.html等）が自動的に再描画される
    this.userId.set(id);
    // localStorageには文字列しか保存できないため、利用者区分コードも文字列にして保持する
    const role = String(roleCode);
    this.role.set(role);
    try {
      // ページ再読み込み後も状態を復元できるよう、ブラウザのlocalStorageにも保存する
      localStorage.setItem(USER_ID_STORAGE_KEY, id);
      localStorage.setItem(ROLE_STORAGE_KEY, role);
    } catch {
      // プライベートブラウジング等でlocalStorageが使えなくても動作は継続する
    }
  }

  /** ログイン状態を解除する。app.tsの「ログアウト」ボタンから呼ばれる。 */
  logout(): void {
    // signalをnullに戻す＝未ログイン状態にする
    this.userId.set(null);
    this.role.set(null);
    try {
      // localStorageに保存していた値も削除する
      localStorage.removeItem(USER_ID_STORAGE_KEY);
      localStorage.removeItem(ROLE_STORAGE_KEY);
    } catch {
      // プライベートブラウジング等でlocalStorageが使えなくても動作は継続する
    }
  }

  // signal()の初期値をlocalStorageから読み出す（ページ再読み込み後もログイン状態を保つため）
  private readInitial(key: string): string | null {
    try {
      // 保存されていなければnullが返る（getItemの仕様）
      return localStorage.getItem(key);
    } catch {
      // localStorage自体が使えない環境では未ログイン扱いにする
      return null;
    }
  }
}
