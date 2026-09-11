// 実行環境: ブラウザ側。URLのパスとコンポーネント（画面）の対応表。
// ここに書かれた設定にしたがって、Angular Routerがページ全体を再読み込みせずに画面を切り替える(SPA)。
import { Routes } from '@angular/router';
import { Home } from './pages/home/home';
import { About } from './pages/about/about';

// B-2: ルーティング疎通確認用の仮ルート。8画面分の本設計はE-1で行う。
export const routes: Routes = [
  { path: '', component: Home },       // "/" にアクセスしたときHomeを表示
  { path: 'about', component: About }, // "/about" にアクセスしたときAboutを表示
];
