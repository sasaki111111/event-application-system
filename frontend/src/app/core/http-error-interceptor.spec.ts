// 実行環境: テスト実行時（ブラウザ相当の環境）。httpErrorInterceptorの単体テスト。
// 認証エラー（401）でログイン状態を解除してログイン画面へ遷移すること、ログインAPIの401は対象外であること、
// サーバーに接続できない場合にメッセージが統一されることを確認する。
import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { ROLE_CODE } from './codes';
import { httpErrorInterceptor } from './http-error-interceptor';
import { LoginUserStore } from './login-user-store';

describe('httpErrorInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let loginUserStore: LoginUserStore;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([httpErrorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    loginUserStore = TestBed.inject(LoginUserStore);
    router = TestBed.inject(Router);
    // 実際の画面遷移は行わず、呼び出されたことだけを確認する
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    loginUserStore.login('1', ROLE_CODE.GENERAL);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('401を受け取るとログイン状態を解除してログイン画面へ遷移し、エラーは呼び出し元にも伝える', () => {
    let received: HttpErrorResponse | undefined;
    http.get('http://localhost:8080/api/events').subscribe({ error: (e) => (received = e) });

    httpMock
      .expectOne('http://localhost:8080/api/events')
      .flush({ message: '認証が必要です' }, { status: 401, statusText: 'Unauthorized' });

    expect(loginUserStore.currentUserId()).toBeNull();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
    expect(received?.status).toBe(401);
  });

  it('ログインAPIの401（メールアドレス・パスワードの誤り）ではログイン状態を変えず遷移もしない', () => {
    let received: HttpErrorResponse | undefined;
    http.post('http://localhost:8080/api/login', {}).subscribe({ error: (e) => (received = e) });

    httpMock
      .expectOne('http://localhost:8080/api/login')
      .flush({ message: 'ログインできませんでした' }, { status: 401, statusText: 'Unauthorized' });

    expect(loginUserStore.currentUserId()).toBe('1');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
    expect(received?.status).toBe(401);
  });

  it('403（権限エラー）ではログイン状態を変えず遷移もしない', () => {
    http.get('http://localhost:8080/api/users').subscribe({ error: () => undefined });

    httpMock
      .expectOne('http://localhost:8080/api/users')
      .flush({ message: '権限がありません' }, { status: 403, statusText: 'Forbidden' });

    expect(loginUserStore.currentUserId()).toBe('1');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('サーバーに接続できない場合（status 0）は共通のメッセージに置き換える', () => {
    let received: HttpErrorResponse | undefined;
    http.get('http://localhost:8080/api/events').subscribe({ error: (e) => (received = e) });

    httpMock.expectOne('http://localhost:8080/api/events').error(new ProgressEvent('error'), { status: 0 });

    expect(received?.error?.message).toBe('サーバーに接続できません。バックエンドが起動しているか確認してください。');
    expect(loginUserStore.currentUserId()).toBe('1');
  });
});
