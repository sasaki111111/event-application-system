// 実行環境: ブラウザ側。アプリ全体を包むルートコンポーネント（<app-root>）。
// RouterOutletの場所に、現在のURLに対応する画面が差し込まれる。
import { Component, signal } from '@angular/core';
import { Router, RouterOutlet, RouterLink } from '@angular/router';
import { DummyUserStore } from './core/dummy-user-store';

@Component({
  imports: [RouterOutlet, RouterLink],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly title = signal('frontend');

  constructor(
    protected readonly dummyUserStore: DummyUserStore,
    private readonly router: Router,
  ) {}

  protected logout(): void {
    this.dummyUserStore.logout();
    this.router.navigateByUrl('/login');
  }
}
