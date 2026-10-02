// 実行環境: ブラウザ側。SC-02の申込完了画面（/events/:id/done）。
// API設計書の備考どおり、専用APIは無く「申込APIのレスポンスをそのまま表示する」だけの画面。
// そのためRouterのnavigation stateで結果を受け取る＝ページの再読み込みには対応しない。
import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ApplicationResponse } from '../../core/application-api';

/**
 * 申込完了画面（SC-02の一部、/events/:id/done）を担当するComponent。
 * APIは呼ばず、event-detail.tsやevent-list.tsが`router.navigate(...)`で遷移する際に
 * 渡した申込結果（state）をそのまま表示するだけの画面。
 *
 * 使用するAngular Service:
 * - `Router`: 画面遷移そのものではなく、直前の画面遷移に乗せられたnavigation state
 *   （渡されたデータ）を読み取るために使う。
 *
 * 画面遷移: 起点はevent-detail.ts／event-list.tsの申込処理（apply()）。
 * この画面からは「イベント一覧へ」リンク（routerLink="/events"）で一覧画面へ戻る。
 */
@Component({
  selector: 'app-apply-done',
  imports: [CommonModule, RouterLink],
  templateUrl: './apply-done.html',
  styleUrl: './apply-done.css',
})
export class ApplyDone {
  protected readonly application: ApplicationResponse | null;

  constructor(router: Router) {
    // getCurrentNavigation()で「今まさに行われている画面遷移」の情報を取得し、
    // そこに乗せられたstate（遷移元が渡したデータ）からapplicationを取り出す。
    // ページを直接開いた場合やリロードした場合はnavigation情報が無いため、applicationはnullになる。
    const state = router.getCurrentNavigation()?.extras?.state as { application?: ApplicationResponse } | undefined;
    this.application = state?.application ?? null;
  }
}
