// 実行環境: ブラウザ側。SC-06 当日受付画面（機能追加、/admin/events/:id/checkin）。
// 申込者一覧の表示（API-18）とチェックイン操作（API-19）を行う。
import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Attendee, CheckInApiService } from '../../core/checkin-api';

@Component({
  selector: 'app-admin-checkin',
  imports: [CommonModule, RouterLink],
  templateUrl: './admin-checkin.html',
  styleUrl: './admin-checkin.css',
})
export class AdminCheckin implements OnInit {
  protected readonly attendees = signal<Attendee[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly checkingInId = signal<number | null>(null);

  private eventId = 0;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly checkInApi: CheckInApiService,
  ) {}

  ngOnInit(): void {
    this.eventId = Number(this.route.snapshot.paramMap.get('id'));
    this.loadAttendees();
  }

  // API-19: 「受付済」以外はbackendが400で拒否する（要件定義書§8 E8）。ボタン自体も受付済のみ活性にする。
  // D-05: チェックイン済みの申込への再実行は、確認のうえ管理者が許可した場合にのみAPIを呼び出す
  protected checkIn(attendee: Attendee): void {
    if (attendee.checkedInAt && !confirm(`「${attendee.userName}」は既にチェックイン済みです。再度チェックインしますか？`)) {
      return;
    }

    this.checkingInId.set(attendee.applicationId);
    this.checkInApi.checkIn(attendee.applicationId).subscribe({
      next: () => this.loadAttendees(),
      error: (err) => {
        this.checkingInId.set(null);
        alert(err.error?.message ?? 'チェックインに失敗しました。');
      },
    });
  }

  private loadAttendees(): void {
    this.loading.set(true);
    this.checkingInId.set(null);
    this.checkInApi.attendees(this.eventId).subscribe({
      next: (attendees) => {
        this.attendees.set(attendees);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(err.error?.message ?? '申込者一覧の取得に失敗しました。');
        this.loading.set(false);
      },
    });
  }
}
