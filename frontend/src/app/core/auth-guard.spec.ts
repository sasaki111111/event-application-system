// 実行環境: ブラウザ側（テスト実行時はNode.js上でVitest／jsdomにより再現）。auth-guard.tsの単体テスト。
// TestBedの使い方はadmin-guard.spec.tsと同様（DummyUserStoreのログイン状態を変えて、
// authGuardの戻り値（true／UrlTree）を確認する）。
// TestBed: Angularのテスト用DIコンテナ
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
// テスト対象の関数
import { authGuard } from './auth-guard';
import { DummyUserStore } from './dummy-user-store';

describe('authGuard', () => {
  let dummyUserStore: DummyUserStore;
  let router: Router;

  beforeEach(() => {
    // 前のテストのログイン状態を引き継がないようにする
    localStorage.clear();
    // テスト用のDIコンテナを組み立てる（ルート定義は空でよい。authGuardはRouter.createUrlTreeしか使わない）
    TestBed.configureTestingModule({
      providers: [provideRouter([])],
    });
    // 組み立てたDIコンテナから、テストで使うサービスのインスタンスを取得する
    dummyUserStore = TestBed.inject(DummyUserStore);
    router = TestBed.inject(Router);
  });

  it('ログイン中は通過させる（true）', () => {
    // 一般利用者としてログイン状態にする
    dummyUserStore.login('1', 'general');

    // インジェクションコンテキストの中でauthGuardを実行する（引数はこのガードが使わないダミー）
    const result = TestBed.runInInjectionContext(() => authGuard(null as never, null as never));

    // ログイン済みなのでtrue（遷移許可）が返ることを確認する
    expect(result).toBe(true);
  });

  it('未ログインの場合はログイン画面へのUrlTreeを返す', () => {
    // beforeEachでlocalStorage.clear()済み＝未ログイン状態のまま実行する
    const result = TestBed.runInInjectionContext(() => authGuard(null as never, null as never));

    // 戻り値がUrlTreeのインスタンスであることを確認する
    expect(result instanceof UrlTree).toBe(true);
    // そのUrlTreeが/loginを指していることを確認する
    expect(router.serializeUrl(result as UrlTree)).toBe('/login');
  });
});
