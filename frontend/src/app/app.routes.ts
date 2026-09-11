// 実行環境: ブラウザ側。URLのパスとコンポーネント（画面）の対応表。
// ここに書かれた設定にしたがって、Angular Routerがページ全体を再読み込みせずに画面を切り替える(SPA)。
import { Routes } from '@angular/router';
import { EventList } from './events/event-list/event-list';
import { EventDetail } from './events/event-detail/event-detail';

// SC-02（画面遷移図）に対応。ログイン画面(SC-01)が無いため、"/"は暫定でイベント一覧へ流す。
// authGuard/adminGuardによるアクセス制御はE-7で追加する。
export const routes: Routes = [
  { path: '', redirectTo: '/events', pathMatch: 'full' },
  { path: 'events', component: EventList },
  { path: 'events/:id', component: EventDetail },
];
