// 実行環境: ブラウザ側。SC-02の申込完了画面（/events/:id/done）。
// API設計書の備考どおり、専用APIは無く「申込APIのレスポンスをそのまま表示する」だけの画面。
// そのためRouterのnavigation stateで結果を受け取る＝ページの再読み込みには対応しない。
import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ApplicationResponse } from '../../core/application-api';

@Component({
  selector: 'app-apply-done',
  imports: [CommonModule, RouterLink],
  templateUrl: './apply-done.html',
  styleUrl: './apply-done.css',
})
export class ApplyDone {
  protected readonly application: ApplicationResponse | null;

  constructor(router: Router) {
    const state = router.getCurrentNavigation()?.extras?.state as { application?: ApplicationResponse } | undefined;
    this.application = state?.application ?? null;
  }
}
