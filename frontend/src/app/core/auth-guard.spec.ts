// 実行環境: ブラウザ側（テスト実行時はNode.js上でVitest／jsdomにより再現）。auth-guard.tsの単体テスト（D-17）。
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { authGuard } from './auth-guard';
import { DummyUserStore } from './dummy-user-store';

describe('authGuard', () => {
  let dummyUserStore: DummyUserStore;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([])],
    });
    dummyUserStore = TestBed.inject(DummyUserStore);
    router = TestBed.inject(Router);
  });

  it('ログイン中は通過させる（true）', () => {
    dummyUserStore.login('1', 'general');

    const result = TestBed.runInInjectionContext(() => authGuard(null as never, null as never));

    expect(result).toBe(true);
  });

  it('未ログインの場合はログイン画面へのUrlTreeを返す', () => {
    const result = TestBed.runInInjectionContext(() => authGuard(null as never, null as never));

    expect(result instanceof UrlTree).toBe(true);
    expect(router.serializeUrl(result as UrlTree)).toBe('/login');
  });
});
