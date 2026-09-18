// 実行環境: ブラウザ側。未定義URLへのアクセス時に表示する画面（画面遷移図§3「全画面 | 未定義URLにアクセス | 404」に対応、機能追加）。
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink],
  templateUrl: './not-found.html',
  styleUrl: './not-found.css',
})
export class NotFound {}
