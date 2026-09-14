// 実行環境: ブラウザ側。SC-04のイベント登録・編集フォーム（D-2対応、E-5）。
// ルートに:idがあれば編集モード（PUT）、無ければ新規登録モード（POST）として動く。
import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EventApiService } from '../../core/event-api';

interface FieldError {
  field: string;
  message: string;
}

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
  });

  protected get isEditMode(): boolean {
    return this.eventId() !== null;
  }

  protected fieldError(field: string): string | null {
    return this.fieldErrors().find((e) => e.field === field)?.message ?? null;
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
        });
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('イベント情報の取得に失敗しました。');
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
    const request = {
      name: value.name,
      startAt: this.toIsoWithSeconds(value.startAt),
      place: value.place,
      capacity: value.capacity,
      applicationDeadline: this.toIsoWithSeconds(value.applicationDeadline),
      description: value.description || undefined,
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
          this.errorMessage.set('保存に失敗しました。');
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
