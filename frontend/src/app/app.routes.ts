// 実行環境: ブラウザ側。URLのパスとコンポーネント（画面）の対応表。
// ここに書かれた設定にしたがって、Angular Routerがページ全体を再読み込みせずに画面を切り替える(SPA)。
import { Routes } from '@angular/router';
import { Login } from './login/login';
import { EventList } from './events/event-list/event-list';
import { EventSearch } from './events/event-search/event-search';
import { EventDetail } from './events/event-detail/event-detail';
import { ApplyDone } from './events/apply-done/apply-done';
import { MyApplications } from './my-applications/my-applications';
import { AdminEventList } from './admin/admin-event-list/admin-event-list';
import { AdminEventForm } from './admin/admin-event-form/admin-event-form';
import { AdminReport } from './admin/admin-report/admin-report';
import { AdminUserList } from './admin/admin-user-list/admin-user-list';
import { authGuard } from './core/auth-guard';
import { adminGuard } from './core/admin-guard';

// SC-01（ログイン）・SC-02・SC-04・SC-05（画面遷移図）に対応。"/"は暫定でイベント一覧へ流す
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
  { path: 'admin/events', component: AdminEventList, canActivate: [authGuard, adminGuard] },
  { path: 'admin/events/new', component: AdminEventForm, canActivate: [authGuard, adminGuard] },
  { path: 'admin/events/:id/edit', component: AdminEventForm, canActivate: [authGuard, adminGuard] },
  { path: 'admin/reports', component: AdminReport, canActivate: [authGuard, adminGuard] },
  { path: 'admin/users', component: AdminUserList, canActivate: [authGuard, adminGuard] },
];
