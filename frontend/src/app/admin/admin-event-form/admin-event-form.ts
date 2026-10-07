// 実行環境: ブラウザ側。SC-022のイベント登録・編集フォーム（E-5）。
// ルートに:idがあれば編集モード（PUT）、無ければ新規登録モード（POST）として動く。
// 機能追加: 主催者名・画像URL・アンケート文言・定員区分（複数、追加/削除可能）の入力欄。
import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EventApiService, EventDuplicateSource } from '../../core/event-api';

interface FieldError {
  field: string;
  message: string;
}

// クライアント側バリデーション（Angular Validators）が引っかかった時のメッセージ。
// バックエンドのEventUpsertRequestのバリデーションメッセージと表現を揃えている。
const REQUIRED_MESSAGES: Record<string, string> = {
  name: 'イベント名を入力してください',
  startAt: '開催日時を入力してください',
  place: '場所を入力してください',
  capacity: '定員を入力してください',
  applicationDeadline: '申込締切を入力してください',
};

@Component({
  selector: 'app-admin-event-form',
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './admin-event-form.html',
  styleUrl: './admin-event-form.css',
})
/**
 * SC-022 イベント登録・編集フォーム画面を担当するComponent。
 * ルート（app.routes.ts）に:idがある場合（/admin/events/:id/edit）は編集モード、
 * 無い場合（/admin/events/new）は新規登録モードとして同じComponent・同じ画面で動く。
 *
 * - ルーティング定義（app.routes.ts）で両ルートとも authGuard・adminGuard が設定されており、
 *   管理者以外のユーザーはアクセスできない（＝管理者専用画面）。
 * - 利用するAngular Service: EventApiService（イベント詳細取得・新規作成・更新）。
 * - フォームの入力管理には Angular の Reactive Forms（ReactiveFormsModule / FormBuilder）を使う。
 *   テンプレート側で value/eventを直接書き換えるのではなく、Component側で保持する
 *   FormGroup（this.form）に対して入力値・入力チェック（Validators）をまとめて管理する方式。
 * - 画面遷移:
 *   - 保存成功時はrouter.navigateByUrl('/admin/events')でイベント管理一覧に戻る。
 *   - 「← イベント管理に戻る」リンク（RouterLink、テンプレート側）からも一覧に戻れる。
 *   - イベント一覧・削除済み一覧の「複製」ボタンからNavigation stateを伴って
 *     新規登録モードに遷移してくることがある（duplicateSource参照）。
 */
export class AdminEventForm implements OnInit {
  protected readonly eventId = signal<number | null>(null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly fieldErrors = signal<FieldError[]>([]);

  // inject()はAngularのDependency Injection（DI）をコンストラクタを介さず呼び出す書き方。
  // constructor(private readonly x: X) {} と同じ意味で、Angular側がインスタンスを渡してくれる。
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly eventApi = inject(EventApiService);

  // イベント一覧・削除済みイベント一覧の「複製」から遷移した場合、Routerのnavigation stateで
  // 複製元の内容を受け取る（apply-doneと同じ方式）。getCurrentNavigation()は遷移中しか取得できないため
  // フィールド初期化の時点（コンストラクタ相当）で読み取っておく
  private readonly duplicateSource: EventDuplicateSource | null =
    // getCurrentNavigation()で今まさに実行中の画面遷移の情報を取得し、その中のstateから
    // duplicateFrom（複製元イベント）を取り出す。渡されていなければnullになる
    (this.router.getCurrentNavigation()?.extras?.state as { duplicateFrom?: EventDuplicateSource } | undefined)
      ?.duplicateFrom ?? null;

  protected get duplicatedFromName(): string | null {
    return this.duplicateSource?.name ?? null;
  }

  /**
   * イベント登録・編集フォーム本体（Angular ReactiveFormsのFormGroup）。
   * 各項目は [初期値, バリデーションルール] の形で定義する。
   * Validators.required は「未入力ならエラー」、Validators.min(1) は「1未満ならエラー」というように、
   * 入力チェックのルールをAngularの標準機能（Validators）で宣言的に指定できる。
   * - name/startAt/place/capacity/applicationDeadline: 必須項目（required）。capacityはさらに1以上必須。
   * - description/organizerName/imageUrl/extraQuestion: 任意項目（バリデーション無し）。
   * - ticketTypes: 定員区分（名称＋区分ごとの定員）を複数件持てるFormArray。0件なら「区分無し」を意味する。
   */
  protected readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    startAt: ['', Validators.required],
    place: ['', Validators.required],
    capacity: [1, [Validators.required, Validators.min(1)]],
    applicationDeadline: ['', Validators.required],
    description: [''],
    // 機能追加（イベント情報の拡張）
    organizerName: [''],
    imageUrl: [''],
    extraQuestion: [''],
    // 機能追加（定員区分）: 区分を1件以上入力した場合はcapacityが自動計算され、上の定員入力は無視される
    ticketTypes: this.fb.nonNullable.array<ReturnType<typeof this.newTicketTypeGroup>>([]),
  });

  /** テンプレート側から定員区分の配列（FormArray）に直接アクセスするためのgetter。 */
  protected get ticketTypesArray() {
    return this.form.controls.ticketTypes;
  }

  /** 定員区分が1件以上登録されているかどうか。区分の有無で定員（capacity）の扱いが変わる。 */
  protected get hasTicketTypes(): boolean {
    return this.ticketTypesArray.length > 0;
  }

  /**
   * 定員区分1件分の入力項目（名称・定員）を持つFormGroupを新規に作る。
   * 名称は必須、定員は必須かつ1以上が条件。
   * @param name 初期値として入れる区分名（既存データの読み込み・複製時に使う）
   * @param capacity 初期値として入れる区分の定員（既存データの読み込み・複製時に使う）
   */
  private newTicketTypeGroup(name = '', capacity = 1) {
    return this.fb.nonNullable.group({
      name: [name, Validators.required],
      capacity: [capacity, [Validators.required, Validators.min(1)]],
    });
  }

  /** 「区分を追加」ボタン（(click)="addTicketType()"）から呼ばれる処理。空の区分入力欄を1件追加する。 */
  protected addTicketType(): void {
    this.ticketTypesArray.push(this.newTicketTypeGroup());
  }

  /**
   * 各区分行の「削除」ボタン（(click)="removeTicketType(index)"）から呼ばれる処理。
   * @param index 削除する区分のFormArray内でのインデックス（0始まり）
   */
  protected removeTicketType(index: number): void {
    this.ticketTypesArray.removeAt(index);
  }

  // 区分が1件以上ある場合、定員は区分の合計として表示のみ行う（実際の計算・保存はbackend側、docs/20_基本設計/22_テーブル定義書.md）
  protected get ticketTypesCapacitySum(): number {
    // reduceで各区分（group）のcapacity値を順番に足し合わせ、合計値を求める
    return this.ticketTypesArray.controls.reduce((sum, group) => sum + (group.value.capacity ?? 0), 0);
  }

  /** trueなら編集モード（既存イベントのPUT更新）、falseなら新規登録モード（POST作成）。 */
  protected get isEditMode(): boolean {
    return this.eventId() !== null;
  }

  /**
   * 指定した項目名（field）について、画面に表示すべきエラーメッセージを返す（無ければnull）。
   * テンプレート側から各入力欄の下に表示するために呼ばれる。
   * サーバー側（保存時）のエラーを優先し、無ければクライアント側（入力中・未入力）のエラーを表示する
   */
  protected fieldError(field: string): string | null {
    // まずサーバー側のエラー一覧（保存時に400で返ってきたもの）に該当項目があれば、それを優先して返す
    const serverMessage = this.fieldErrors().find((e) => e.field === field)?.message;
    if (serverMessage) {
      return serverMessage;
    }

    // 対応するFormControlを取得する。存在しない、現在のところ入力値が正しい、または
    // まだ一度も触られていない（touchedでもdirtyでもない）場合はエラー無し（null）とする
    const control = this.form.get(field);
    if (!control || control.valid || !(control.touched || control.dirty)) {
      return null;
    }
    // 必須違反（未入力）の場合は、項目ごとに用意したメッセージ（REQUIRED_MESSAGES）を返す
    if (control.hasError('required')) {
      return REQUIRED_MESSAGES[field] ?? '入力してください';
    }
    // 最小値違反（1未満）の場合のメッセージを返す
    if (control.hasError('min')) {
      return '1以上で入力してください';
    }
    return null;
  }

  /**
   * Angularのライフサイクルフックの一つ。Componentが画面に表示される直前に一度だけ実行される。
   * URLに:idがあるかどうかで新規登録モード・編集モードを判定し、編集モードなら既存イベントの
   * データをAPIから取得してフォームに反映する（patchValueで既存のFormGroupに値を流し込む）。
   */
  ngOnInit(): void {
    // URLに:idが無ければ新規登録モード
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam === null) {
      this.applyDuplicateSource(); // 新規登録モード（複製元があればあらかじめ入力する）
      return;
    }

    // :idがあれば編集モード。文字列を数値に変換してeventIdに保持する
    const id = Number(idParam);
    this.eventId.set(id);
    // 既存データ取得中は読み込み中表示にする
    this.loading.set(true);
    // .subscribe({ next, error }) はObservable（非同期で届くAPI応答）から結果を受け取る書き方
    this.eventApi.detail(id).subscribe({
      next: (event) => {
        // 取得した既存イベントの値をフォームに反映する（patchValueは一部項目だけの更新も可能）
        this.form.patchValue({
          name: event.name,
          // 開催日時・申込締切はサーバー側の秒付きISO8601形式から、
          // datetime-local入力欄が受け付ける形式（秒を含まない）に変換してセットする
          startAt: this.toDatetimeLocal(event.startAt),
          place: event.place,
          capacity: event.capacity,
          applicationDeadline: this.toDatetimeLocal(event.applicationDeadline),
          description: event.description,
          // 任意項目はnullの場合に空文字へ置き換える（入力欄にnullを渡せないため）
          organizerName: event.organizerName ?? '',
          imageUrl: event.imageUrl ?? '',
          extraQuestion: event.extraQuestion ?? '',
        });
        // 既存イベントに登録されている定員区分の件数分、区分入力行をFormArrayに追加する
        for (const ticketType of event.ticketTypes) {
          this.ticketTypesArray.push(this.newTicketTypeGroup(ticketType.name, ticketType.capacity));
        }
        // データ反映が終わったので読み込み中表示を終える
        this.loading.set(false);
      },
      error: (err) => {
        // 取得失敗時はエラーメッセージを表示し、読み込み中表示を終える
        this.errorMessage.set(err.error?.message ?? 'イベント情報の取得に失敗しました。');
        this.loading.set(false);
      },
    });
  }

  /**
   * フォーム下部の「保存」ボタン（送信ボタン）から呼ばれる、入力内容の送信処理。
   * 1. this.form.invalid（Validatorsで設定した必須・最小値などのチェックに1つでも違反があればtrue）
   *    の場合は送信を中止し、markAllAsTouched()で全項目を「操作済み」扱いにして、
   *    未入力のままだった項目にもエラーメッセージが表示されるようにする。
   * 2. 入力値を取り出し（getRawValue）、区分（ticketTypes）が1件以上あれば定員（capacity）を
   *    区分の定員合計に差し替えてからAPIに送信する。
   * 3. 新規登録モードならcreate（POST）、編集モードならupdate（PUT）を呼び、成功したら
   *    イベント管理一覧（/admin/events）に遷移する。
   * 4. サーバー側バリデーションエラー（400＋errors配列）は各項目のエラー表示に反映し、
   *    権限エラー（403）・イベント未存在（404）はエラーメッセージとして表示する。
   */
  protected submit(): void {
    // フォーム全体にバリデーションエラーが1つでもあれば送信を中止する
    if (this.form.invalid) {
      // 全項目を「操作済み」扱いにし、未入力のままだった項目にもエラー表示を出す
      this.form.markAllAsTouched();
      return;
    }

    // 前回表示していたエラーをクリアし、保存中状態にする
    this.errorMessage.set(null);
    this.fieldErrors.set([]);
    this.saving.set(true);

    // フォームの入力値を取り出す（getRawValueはdisabledな項目の値も含めて取得する）
    const value = this.form.getRawValue();
    // 区分が1件以上ある場合、画面には合計値を表示しているが対応するinputにformControlNameを
    // 付けていない（自動計算の見た目のみのため）ので、capacityフィールド自体もその合計値を送る
    const capacity = this.hasTicketTypes ? this.ticketTypesCapacitySum : value.capacity;
    // サーバーに送信するリクエストボディを組み立てる
    const request = {
      name: value.name,
      // datetime-local形式から秒付きISO8601形式に変換して送信する
      startAt: this.toIsoWithSeconds(value.startAt),
      place: value.place,
      capacity,
      applicationDeadline: this.toIsoWithSeconds(value.applicationDeadline),
      // 任意項目は空文字なら未入力として扱い、undefinedに変換して送信する
      description: value.description || undefined,
      organizerName: value.organizerName || undefined,
      imageUrl: value.imageUrl || undefined,
      extraQuestion: value.extraQuestion || undefined,
      // 区分は常に現在のフォームの内容で全置換する（0件なら「区分無し」に確定させる）
      ticketTypes: value.ticketTypes,
    };

    // eventIdがnullなら新規作成（POST）、値があれば更新（PUT）を呼ぶ
    const id = this.eventId();
    const result = id === null ? this.eventApi.create(request) : this.eventApi.update(id, request);

    result.subscribe({
      // 保存成功時はイベント管理一覧へ遷移する
      next: () => this.router.navigateByUrl('/admin/events'),
      error: (err) => {
        // 保存失敗時は保存中状態を解除し、エラーの種類ごとに表示を切り替える
        this.saving.set(false);
        if (err.status === 400 && err.error?.errors) {
          // サーバー側バリデーションエラーは各項目のエラー表示に反映する
          this.fieldErrors.set(err.error.errors);
        } else if (err.status === 403) {
          // 管理者権限が無い場合のエラー
          this.errorMessage.set('権限がありません（管理者としてログインしてください）。');
        } else if (err.status === 404) {
          // 編集対象のイベントが見つからない場合のエラー
          this.errorMessage.set('イベントが見つかりません。');
        } else {
          // その他の想定外のエラー
          this.errorMessage.set(err.error?.message ?? '保存に失敗しました。');
        }
      },
    });
  }

  /**
   * 複製元の内容を新規登録フォームに複写する。開催日時・申込締切は複写対象外（未来日時必須のバリデーションに
   * 抵触しうるため空欄のまま管理者に入力させる）。イベント名は複製元と全く同じ値をそのまま複写する。
   */
  private applyDuplicateSource(): void {
    // 複製元データが渡されていなければ（通常の新規登録）何もしない
    const source = this.duplicateSource;
    if (!source) {
      return;
    }
    // 複製元の値をフォームに反映する（開催日時・申込締切は含めない＝未入力のまま）
    this.form.patchValue({
      name: source.name,
      place: source.place,
      capacity: source.capacity,
      description: source.description ?? '',
      organizerName: source.organizerName ?? '',
      imageUrl: source.imageUrl ?? '',
      extraQuestion: source.extraQuestion ?? '',
    });
    // 複製元に登録されていた定員区分の件数分、区分入力行をFormArrayに追加する
    for (const ticketType of source.ticketTypes) {
      this.ticketTypesArray.push(this.newTicketTypeGroup(ticketType.name, ticketType.capacity));
    }
  }

  /** "2027-03-01T10:00:00" → datetime-local入力欄向けの"2027-03-01T10:00" */
  private toDatetimeLocal(iso: string): string {
    return iso.slice(0, 16);
  }

  /** datetime-local入力欄の"2027-03-01T10:00" → 秒付きISO8601"2027-03-01T10:00:00" */
  private toIsoWithSeconds(local: string): string {
    return local.length === 16 ? `${local}:00` : local;
  }
}
