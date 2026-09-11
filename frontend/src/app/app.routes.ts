import { Routes } from '@angular/router';
import { Home } from './pages/home/home';
import { About } from './pages/about/about';

// B-2: ルーティング疎通確認用の仮ルート。8画面分の本設計はE-1で行う。
export const routes: Routes = [
  { path: '', component: Home },
  { path: 'about', component: About },
];
