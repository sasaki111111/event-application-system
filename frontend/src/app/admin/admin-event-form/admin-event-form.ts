// 実行環境: ブラウザ側。SC-04のイベント登録・編集フォーム（D-2対応、E-5）。
// ルートに:idがあれば編集モード（PUT）、無ければ新規登録モード（POST）として動く。
// 機能追加: 主催者名・画像URL・カテゴリ・アンケート文言・定員区分（複数、追加/削除可能）の入力欄。
import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EventApiService } from '../../core/event-api';

interface FieldError {
  field: string;
  message: string;
}

// クライアント側バリデーション（Angular Validators）が引っかかった時のメッセージ。
// バックエンドのEventUpsertRequestのバリデーションメッセージと表現を揃えている。
const REQUIRED_MESSAGES: Record<string, string> = {
  name: '名前を入力してください',
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
export class AdminEventForm implements OnInit {
  protected readonly eventId = signal<number | null>(null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly fieldErrors = signal<FieldError[]>([]);

  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly eventApi = inject(EventApiService);

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
    category: [''],
    extraQuestion: [''],
    // 機能追加（定員区分）: 区分を1件以上入力した場合はcapacityが自動計算され、上の定員入力は無視される
    ticketTypes: this.fb.nonNullable.array<ReturnType<typeof this.newTicketTypeGroup>>([]),
  });

  protected get ticketTypesArray() {
    return this.form.controls.ticketTypes;
  }

  protected get hasTicketTypes(): boolean {
    return this.ticketTypesArray.length > 0;
  }

  private newTicketTypeGroup(name = '', capacity = 1) {
    return this.fb.nonNullable.group({
      name: [name, Validators.required],
      capacity: [capacity, [Validators.required, Validators.min(1)]],
    });
  }

  protected addTicketType(): void {
    this.ticketTypesArray.push(this.newTicketTypeGroup());
  }

  protected removeTicketType(index: number): void {
    this.ticketTypesArray.removeAt(index);
  }

  // 区分が1件以上ある場合、定員は区分の合計として表示のみ行う（実際の計算・保存はbackend側、テーブル定義書_v2.0.md§2.2）
  protected get ticketTypesCapacitySum(): number {
    return this.ticketTypesArray.controls.reduce((sum, group) => sum + (group.value.capacity ?? 0), 0);
  }

  protected get isEditMode(): boolean {
    return this.eventId() !== null;
  }

  // サーバー側（保存時）のエラーを優先し、無ければクライアント側（入力中・未入力）のエラーを表示する
  protected fieldError(field: string): string | null {
    const serverMessage = this.fieldErrors().find((e) => e.field === field)?.message;
    if (serverMessage) {
      return serverMessage;
    }

    const control = this.form.get(field);
    if (!control || control.valid || !(control.touched || control.dirty)) {
      return null;
    }
    if (control.hasError('required')) {
      return REQUIRED_MESSAGES[field] ?? '入力してください';
    }
    if (control.hasError('min')) {
      return '1以上で入力してください';
    }
    return null;
  }

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam === null) {
      return; // 新規登録モード
    }

    const id = Number(idParam);
    this.eventId.set(id);
    this.loading.set(true);
    this.eventApi.detail(id).subscribe({
      next: (event) => {
        this.form.patchValue({
          name: event.name,
          startAt: this.toDatetimeLocal(event.startAt),
          place: event.place,
          capacity: event.capacity,
          applicationDeadline: this.toDatetimeLocal(event.applicationDeadline),
          description: event.description,
          organizerName: event.organizerName ?? '',
          imageUrl: event.imageUrl ?? '',
          category: event.category ?? '',
          extraQuestion: event.extraQuestion ?? '',
        });
        for (const ticketType of event.ticketTypes) {
          this.ticketTypesArray.push(this.newTicketTypeGroup(ticketType.name, ticketType.capacity));
        }
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(err.error?.message ?? 'イベント情報の取得に失敗しました。');
        this.loading.set(false);
      },
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMessage.set(null);
    this.fieldErrors.set([]);
    this.saving.set(true);

    const value = this.form.getRawValue();
    // 区分が1件以上ある場合、画面には合計値を表示しているが対応するinputにformControlNameを
    // 付けていない（自動計算の見た目のみのため）ので、capacityフィールド自体もその合計値を送る
    const capacity = this.hasTicketTypes ? this.ticketTypesCapacitySum : value.capacity;
    const request = {
      name: value.name,
      startAt: this.toIsoWithSeconds(value.startAt),
      place: value.place,
      capacity,
      applicationDeadline: this.toIsoWithSeconds(value.applicationDeadline),
      description: value.description || undefined,
      organizerName: value.organizerName || undefined,
      imageUrl: value.imageUrl || undefined,
      category: value.category || undefined,
      extraQuestion: value.extraQuestion || undefined,
      // 区分は常に現在のフォームの内容で全置換する（0件なら「区分無し」に確定させる）
      ticketTypes: value.ticketTypes,
    };

    const id = this.eventId();
    const result = id === null ? this.eventApi.create(request) : this.eventApi.update(id, request);

    result.subscribe({
      next: () => this.router.navigateByUrl('/admin/events'),
      error: (err) => {
        this.saving.set(false);
        if (err.status === 400 && err.error?.errors) {
          this.fieldErrors.set(err.error.errors);
        } else if (err.status === 403) {
          this.errorMessage.set('権限がありません（管理者としてログインしてください）。');
        } else if (err.status === 404) {
          this.errorMessage.set('イベントが見つかりません。');
        } else {
          this.errorMessage.set(err.error?.message ?? '保存に失敗しました。');
        }
      },
    });
  }

  // "2027-03-01T10:00:00" → datetime-local入力欄向けの"2027-03-01T10:00"
  private toDatetimeLocal(iso: string): string {
    return iso.slice(0, 16);
  }

  // datetime-local入力欄の"2027-03-01T10:00" → 秒付きISO8601"2027-03-01T10:00:00"
  private toIsoWithSeconds(local: string): string {
    return local.length === 16 ? `${local}:00` : local;
  }
}
