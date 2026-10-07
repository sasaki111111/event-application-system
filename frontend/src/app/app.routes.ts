// 実行環境: ブラウザ側。URLのパスとコンポーネント（画面）の対応表。
// ここに書かれた設定にしたがって、Angular Routerがページ全体を再読み込みせずに画面を切り替える(SPA)。
//
// [Angularの基礎: ルーティング] Routes型の配列1件が1つのルート定義で、pathがURLパス、
// componentがそのパスで表示する画面（Component）を表す。canActivateには、その画面に遷移してよいか
// を判定するルートガード（CanActivateFn）を配列で指定でき、配列内のガードは前から順に実行され、
// 1つでも遷移を拒否（false／UrlTree）したらそこで止まる。この設定全体はapp.config.tsの
// provideRouter(routes)でAngular Routerに登録される。
import { Routes } from '@angular/router';
import { Login } from './login/login';
import { EventList } from './events/event-list/event-list';
import { EventSearch } from './events/event-search/event-search';
import { EventDetail } from './events/event-detail/event-detail';
import { ApplyDone } from './events/apply-done/apply-done';
import { MyApplications } from './my-applications/my-applications';
import { PasswordChange } from './password-change/password-change';
import { AdminDashboard } from './admin/admin-dashboard/admin-dashboard';
import { AdminEventList } from './admin/admin-event-list/admin-event-list';
import { AdminEventForm } from './admin/admin-event-form/admin-event-form';
import { AdminReport } from './admin/admin-report/admin-report';
import { AdminUserList } from './admin/admin-user-list/admin-user-list';
import { AdminUserDetail } from './admin/admin-user-detail/admin-user-detail';
import { AdminCommentList } from './admin/admin-comment-list/admin-comment-list';
import { AdminDeletedEvents } from './admin/admin-deleted-events/admin-deleted-events';
import { AdminCheckin } from './admin/admin-checkin/admin-checkin';
import { NotFound } from './not-found/not-found';
import { authGuard } from './core/auth-guard';
import { adminGuard } from './core/admin-guard';

// SC-010（ログイン）・SC-020・SC-022・SC-023（画面遷移図）に対応。"/"は暫定でイベント一覧へ流す
// （未ログインならauthGuardがさらに/loginへ戻す）。
// authGuardは未ログインを弾き、adminGuardは管理者以外の/admin/**アクセスを弾く（E-7）。
export const routes: Routes = [
  { path: '', redirectTo: '/events', pathMatch: 'full' },
  { path: 'login', component: Login },
  { path: 'events', component: EventList, canActivate: [authGuard] },
  { path: 'events/search', component: EventSearch, canActivate: [authGuard] },
  { path: 'events/:id', component: EventDetail, canActivate: [authGuard] },
  { path: 'events/:id/done', component: ApplyDone, canActivate: [authGuard] },
  { path: 'my/applications', component: MyApplications, canActivate: [authGuard] },
  // SC-011 パスワード変更（一般利用者・管理者とも利用できる）
  { path: 'my/password', component: PasswordChange, canActivate: [authGuard] },
  { path: 'admin/dashboard', component: AdminDashboard, canActivate: [authGuard, adminGuard] },
  { path: 'admin/events', component: AdminEventList, canActivate: [authGuard, adminGuard] },
  { path: 'admin/events/new', component: AdminEventForm, canActivate: [authGuard, adminGuard] },
  { path: 'admin/events/deleted', component: AdminDeletedEvents, canActivate: [authGuard, adminGuard] },
  { path: 'admin/events/:id/edit', component: AdminEventForm, canActivate: [authGuard, adminGuard] },
  // 機能追加（当日受付）
  { path: 'admin/events/:id/checkin', component: AdminCheckin, canActivate: [authGuard, adminGuard] },
  { path: 'admin/reports', component: AdminReport, canActivate: [authGuard, adminGuard] },
  { path: 'admin/users', component: AdminUserList, canActivate: [authGuard, adminGuard] },
  // （機能追加）: 利用者詳細（SC-141）
  { path: 'admin/users/:id', component: AdminUserDetail, canActivate: [authGuard, adminGuard] },
  // （機能追加）: コメントモデレーション（SC-150）
  { path: 'admin/comments', component: AdminCommentList, canActivate: [authGuard, adminGuard] },
  // 画面遷移図§3「未定義URLにアクセス→404」（機能追加）。ワイルドカードは必ず配列の最後に置く
  { path: '**', component: NotFound },
];
