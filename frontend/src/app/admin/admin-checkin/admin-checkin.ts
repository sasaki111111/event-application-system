// 実行環境: ブラウザ側。SC-030 当日受付画面（機能追加、/admin/events/:id/checkin）。
// 申込者一覧の表示（API-18）とチェックイン操作（API-19）を行う。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Attendee, CheckInApiService } from '../../core/checkin-api';
import { EventApiService } from '../../core/event-api';
import { STATUS_CODE } from '../../core/codes';

@Component({
  selector: 'app-admin-checkin',
  imports: [CommonModule, RouterLink],
  templateUrl: './admin-checkin.html',
  styleUrl: './admin-checkin.css',
})
/**
 * SC-030 当日受付画面（/admin/events/:id/checkin）を担当するComponent。
 *
 * - ルーティング定義（app.routes.ts）でこのパスには authGuard・adminGuard の両方が
 *   設定されているため、未ログインのユーザーや管理者以外のロールのユーザーはこの画面に
 *   到達できない（＝管理者専用画面）。
 * - 利用するAngular Service:
 *   - CheckInApiService: 申込者一覧の取得（API-18）とチェックイン操作（API-19）を行う。
 *   - EventApiService: アンケート設問文言（extraQuestion）を取得するためにイベント詳細を読む。
 * - 画面遷移: 画面上部の「← イベント管理に戻る」リンク（RouterLink）から /admin/events に戻る。
 *   この画面へは管理者側のイベント一覧・編集画面などから遷移してくる想定。
 *
 * `implements OnInit` は、Angularが用意しているライフサイクルフックの一つを使うことを表す
 * 宣言。ngOnInitメソッドは、このComponentが画面に実際に表示される直前に一度だけ自動的に
 * 呼び出される（コンストラクタの後、画面表示の準備が整った時点で呼ばれる初期化処理の定番の場所）。
 */
export class AdminCheckin implements OnInit {
  // テンプレートで申込状況コードを判定するために公開する（文字列の表示名では判定しない）
  protected readonly StatusCode = STATUS_CODE;
  protected readonly attendees = signal<Attendee[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly checkingInId = signal<number | null>(null);

  // （機能追加）: アンケートの質問文言はAP-021（イベント詳細）から取得する。未設定ならNULL
  protected readonly extraQuestion = signal<string | null>(null);

  private eventId = 0;

  /**
   * コンストラクタ。`private readonly route: ActivatedRoute` のような書き方は
   * Angularのコンストラクタインジェクション（Dependency Injection, DI）で、
   * Angular側が自動的にServiceのインスタンスを渡してくれる仕組み。
   * - route: URL（/admin/events/:id/checkin）の :id 部分（イベントID）を読み取るために使う。
   * - checkInApi, eventApi: 上記クラスJSDoc参照。
   */
  constructor(
    private readonly route: ActivatedRoute,
    private readonly checkInApi: CheckInApiService,
    private readonly eventApi: EventApiService,
  ) {}

  /**
   * Angularのライフサイクルフックの一つ。このComponentが画面に表示される直前に
   * 一度だけ実行される（コンストラクタとは別物で、DIが完了した後に呼ばれる）。
   * ここでは、URLからイベントIDを取り出し、申込者一覧の読み込みと、
   * アンケート設問文言（あれば）の取得を行っている。
   */
  ngOnInit(): void {
    // URLの:id部分（文字列）を取得し、Number()で数値に変換してイベントIDとして保持する
    this.eventId = Number(this.route.snapshot.paramMap.get('id'));
    // 申込者一覧を読み込む（下のloadAttendees()を呼ぶ）
    this.loadAttendees();

    // アンケート質問文言の取得に失敗しても、申込者一覧の表示はブロックしない
    // .subscribe({ next, error }) は、Observable（非同期で届くデータの流れ）から
    // 結果を受け取るための書き方。next は成功時、error は失敗時に呼ばれる関数を渡す。
    this.eventApi.detail(this.eventId).subscribe({
      // 取得できたイベント詳細のextraQuestion（アンケート文言、無ければnull）をそのまま反映する
      next: (event) => this.extraQuestion.set(event.extraQuestion),
      // 失敗時は何もしない（アンケート列を表示しないだけで、一覧表示自体は継続する）
      error: () => {},
    });
  }

  /**
   * テンプレート側の「チェックイン」「再チェックイン」ボタン（(click)="checkIn(attendee)"）
   * から呼ばれる処理。引数attendeeはクリックされた行の申込者データ。
   * API-19: 「受付済」以外はbackendが400で拒否する（要件定義書§8 E8）。ボタン自体も受付済のみ活性にする。
   * チェックイン済みの申込への再実行は、確認のうえ管理者が許可した場合にのみAPIを呼び出す
   */
  protected checkIn(attendee: Attendee): void {
    // すでにチェックイン済みの場合は、confirm()で再チェックインしてよいか確認する。
    // キャンセルされたら（confirmがfalseを返したら）ここで処理を中断する
    if (attendee.checkedInAt && !confirm(`「${attendee.userName}」は既にチェックイン済みです。再度チェックインしますか？`)) {
      return;
    }

    // どの申込を処理中かをsignalに記録する（ボタンの「処理中...」表示・多重クリック防止に使う）
    this.checkingInId.set(attendee.applicationId);
    // チェックインAPIを呼び出す
    this.checkInApi.checkIn(attendee.applicationId).subscribe({
      // 成功したら一覧を再取得し、最新の受付状況を画面に反映する
      next: () => this.loadAttendees(),
      error: (err) => {
        // 失敗したら処理中状態を解除し、エラー内容をアラートで表示する
        this.checkingInId.set(null);
        alert(err.error?.message ?? 'チェックインに失敗しました。');
      },
    });
  }

  /** 当該イベントの申込者一覧（attendees）をAPIから再取得し、画面の状態を更新する。 */
  private loadAttendees(): void {
    // 読み込み中表示に切り替える
    this.loading.set(true);
    // 処理中だった行の状態をクリアする（再取得のたびにリセット）
    this.checkingInId.set(null);
    // 当該イベントの申込者一覧を取得する
    this.checkInApi.attendees(this.eventId).subscribe({
      next: (attendees) => {
        // 取得した一覧をsignalに反映し、読み込み中表示を終える
        this.attendees.set(attendees);
        this.loading.set(false);
      },
      error: (err) => {
        // 取得失敗時はエラーメッセージを表示し、読み込み中表示を終える
        this.errorMessage.set(err.error?.message ?? '申込者一覧の取得に失敗しました。');
        this.loading.set(false);
      },
    });
  }
}
