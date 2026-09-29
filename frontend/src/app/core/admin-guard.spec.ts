// 実行環境: ブラウザ側（テスト実行時はNode.js上でVitest／jsdomにより再現）。admin-guard.tsの単体テスト（D-17）。
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { adminGuard } from './admin-guard';
import { DummyUserStore } from './dummy-user-store';

describe('adminGuard', () => {
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

  it('管理者（role=admin）は通過させる（true）', () => {
    dummyUserStore.login('2', 'admin');

    const result = TestBed.runInInjectionContext(() => adminGuard(null as never, null as never));

    expect(result).toBe(true);
  });

  it('一般利用者（role=general）はイベント一覧へのUrlTreeを返す（D-03: role基準の判定）', () => {
    dummyUserStore.login('1', 'general');

    const result = TestBed.runInInjectionContext(() => adminGuard(null as never, null as never));

    expect(result instanceof UrlTree).toBe(true);
    expect(router.serializeUrl(result as UrlTree)).toBe('/events');
  });
});
