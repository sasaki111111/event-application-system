// 実行環境: ブラウザ側。URLのパスとコンポーネント（画面）の対応表。
// ここに書かれた設定にしたがって、Angular Routerがページ全体を再読み込みせずに画面を切り替える(SPA)。
import { Routes } from '@angular/router';
import { EventList } from './events/event-list/event-list';
import { EventDetail } from './events/event-detail/event-detail';
import { ApplyDone } from './events/apply-done/apply-done';
import { MyApplications } from './my-applications/my-applications';
import { AdminEventList } from './admin/admin-event-list/admin-event-list';
import { AdminEventForm } from './admin/admin-event-form/admin-event-form';
import { adminGuard } from './core/admin-guard';

// SC-02・SC-04（画面遷移図）に対応。ログイン画面(SC-01)が無いため、"/"は暫定でイベント一覧へ流す。
// adminGuardは管理者以外の/admin/**アクセスを弾く（E-7の先行実装）。authGuard（未ログイン制御）はE-2以降で追加する。
export const routes: Routes = [
  { path: '', redirectTo: '/events', pathMatch: 'full' },
  { path: 'events', component: EventList },
  { path: 'events/:id', component: EventDetail },
  { path: 'events/:id/done', component: ApplyDone },
  { path: 'my/applications', component: MyApplications },
  { path: 'admin/events', component: AdminEventList, canActivate: [adminGuard] },
  { path: 'admin/events/new', component: AdminEventForm, canActivate: [adminGuard] },
  { path: 'admin/events/:id/edit', component: AdminEventForm, canActivate: [adminGuard] },
];
