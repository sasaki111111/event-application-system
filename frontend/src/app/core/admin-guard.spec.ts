// 実行環境: ブラウザ側（テスト実行時はNode.js上でVitest／jsdomにより再現）。admin-guard.tsの単体テスト。
//
// [テストの概要] adminGuardは引数を取らずDummyUserStoreのロールだけを見て判定するため、
// ここではTestBed（Angularのテスト用DIコンテナ）でDummyUserStoreとRouterを用意し、
// ログイン状態を変えながらadminGuardの戻り値（true／UrlTree）を確認する。
// TestBed: Angularのテスト用DIコンテナ。本番のinject()相当の仕組みをテストコードから使うためのAPI
import { TestBed } from '@angular/core/testing';
// provideRouter: テスト用にAngular Routerを有効化するprovider。UrlTree: ガードが返す遷移先の型
import { provideRouter, Router, UrlTree } from '@angular/router';
// テスト対象の関数
import { adminGuard } from './admin-guard';
import { DummyUserStore } from './dummy-user-store';

describe('adminGuard', () => {
  // テスト対象が依存するサービスのインスタンスを各テストで使えるように変数として保持する
  let dummyUserStore: DummyUserStore;
  let router: Router;

  // 各it()ブロックの実行前に毎回呼ばれる準備処理
  beforeEach(() => {
    // 前のテストのログイン状態が残らないよう、localStorageを空にする
    localStorage.clear();
    // TestBed.configureTestingModule(): このテスト内で使うAngularのDIコンテナ（テスト専用）を
    // 組み立てる。provideRouter([])で、Routerサービス自体は使えるが実際のルート定義は空にしている
    // （adminGuardはRouter.createUrlTreeしか使わないため、ルート定義そのものは不要）。
    TestBed.configureTestingModule({
      providers: [provideRouter([])],
    });
    // TestBed.inject(): 組み立てたテスト用DIコンテナから、本番コードのinject()と同様にサービスの
    // インスタンスを取得する（テストコード側の取得方法）。
    dummyUserStore = TestBed.inject(DummyUserStore);
    router = TestBed.inject(Router);
  });

  it('管理者（role=admin）は通過させる（true）', () => {
    // DummyUserStoreをadmin役でログイン状態にする
    dummyUserStore.login('2', 'admin');

    // adminGuard自体はinject()を使う関数型ガードなので、Angularの依存注入が効く文脈
    // （インジェクションコンテキスト）の中でしか呼び出せない。TestBed.runInInjectionContext()で
    // その文脈を一時的に作ってadminGuardを実行している。
    // 引数（null as never, null as never）はこのガードが使わないルート情報・遷移情報のダミー
    const result = TestBed.runInInjectionContext(() => adminGuard(null as never, null as never));

    // 管理者なのでtrue（遷移許可）が返ることを確認する
    expect(result).toBe(true);
  });

  it('一般利用者（role=general）はイベント一覧へのUrlTreeを返す（role基準の判定）', () => {
    // 一般利用者役でログイン状態にする
    dummyUserStore.login('1', 'general');

    const result = TestBed.runInInjectionContext(() => adminGuard(null as never, null as never));

    // 戻り値がUrlTreeのインスタンスであることを確認する
    expect(result instanceof UrlTree).toBe(true);
    // そのUrlTreeを実際のURL文字列に変換し、/eventsになっていることを確認する
    expect(router.serializeUrl(result as UrlTree)).toBe('/events');
  });
});
