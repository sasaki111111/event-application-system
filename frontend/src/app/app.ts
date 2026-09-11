// 実行環境: ブラウザ側。アプリ全体を包むルートコンポーネント（<app-root>）。
// RouterOutletの場所に、現在のURLに対応する画面（Home/About等）が差し込まれる。
import { Component, signal } from '@angular/core';
import { RouterOutlet, RouterLink } from '@angular/router';

@Component({
  imports: [RouterOutlet, RouterLink],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly title = signal('frontend');
}
