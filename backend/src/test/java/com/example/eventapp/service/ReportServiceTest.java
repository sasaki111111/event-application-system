package com.example.eventapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.common.CurrentUser;
import com.example.eventapp.dto.EventReportResponse;
import com.example.eventapp.entity.Application;
import com.example.eventapp.entity.ApplicationStatus;
import com.example.eventapp.entity.Event;
import com.example.eventapp.entity.TicketType;
import com.example.eventapp.entity.User;
import com.example.eventapp.repository.ApplicationRepository;
import com.example.eventapp.repository.EventRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ReportService（申込実績の集計・CSV出力の業務ロジック）に対する単体テスト。
 * JUnit5とMockitoを使用する。Repository（DBアクセスを担うクラス）はすべてモック化（偽装）し、
 * 本物のDBに接続せずに「充足率の丸め」「CSVのエスケープ」「CSVインジェクション対策」等だけを検証する。
 */
// 実行環境: サーバー側（JVM）。docs/12_テスト仕様書.md 12-3 UT-RPT-01〜07に対応する。
class ReportServiceTest {

    private EventRepository eventRepository;
    private ApplicationRepository applicationRepository;
    private AuthContext authContext;
    private ReportService reportService;

    // UTF-8 BOM（U+FEFF）。ReportServiceと同じく文字コードから生成する
    private static final String BOM = String.valueOf((char) 0xFEFF);
    private static final String HEADER = "イベント名,申込者名,申込日時,ステータス,アンケート回答,参加区分";
    private static final LocalDateTime APPLIED_AT = LocalDateTime.of(2026, 10, 5, 9, 30, 15);

    // 各@Testメソッドの実行前に毎回呼ばれ、テストごとに新しいモックとServiceを用意する
    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        authContext = mock(AuthContext.class);
        // 操作ログ（docs/11_ログ設計書.md 11-5）出力のため、toCsv()はログイン中管理者を参照する
        when(authContext.getCurrentUser()).thenReturn(new CurrentUser(2L, "管理者", "admin"));
        reportService = new ReportService(eventRepository, applicationRepository, authContext);
    }

    // テスト用の「ID・名前・定員・受付済数」を持つEventのモックを組み立てるヘルパーメソッド
    private Event event(Long id, String name, int capacity, long acceptedCount) {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(id);
        when(event.getName()).thenReturn(name);
        when(event.getCapacity()).thenReturn(capacity);
        when(applicationRepository.countByEvent_IdAndStatus(id, ApplicationStatus.ACCEPTED)).thenReturn(acceptedCount);
        return event;
    }

    // テスト用の申込（CSVの1行分）のモックを組み立てるヘルパーメソッド。ticketTypeNameがnullなら区分なし
    private Application application(String eventName, String userName, String status, String extraAnswer,
            String ticketTypeName) {
        Event event = mock(Event.class);
        when(event.getName()).thenReturn(eventName);
        User user = mock(User.class);
        when(user.getName()).thenReturn(userName);
        Application application = mock(Application.class);
        when(application.getEvent()).thenReturn(event);
        when(application.getUser()).thenReturn(user);
        when(application.getAppliedAt()).thenReturn(APPLIED_AT);
        when(application.getStatus()).thenReturn(status);
        when(application.getExtraAnswer()).thenReturn(extraAnswer);
        if (ticketTypeName != null) {
            TicketType ticketType = mock(TicketType.class);
            when(ticketType.getName()).thenReturn(ticketTypeName);
            when(application.getTicketType()).thenReturn(ticketType);
        }
        return application;
    }

    // toCsv()の結果からBOMを除き、行（\r\n区切り）に分割するヘルパーメソッド
    private String[] csvLines(List<Application> applications) {
        when(applicationRepository.findAllByEvent_DeletedAtIsNullOrderByEvent_StartAtAsc()).thenReturn(applications);
        return reportService.toCsv().substring(BOM.length()).split("\r\n");
    }

    // 正常系（UT-RPT-01）: 充足率は小数第2位までに四捨五入される（2÷3＝0.666…→0.67）
    @Test
    void summarize_正常系_充足率は小数第2位までに丸められる() {
        Event event = event(1L, "定員3のイベント", 3, 2L);
        when(eventRepository.findAllByDeletedAtIsNullOrderByStartAtAsc()).thenReturn(List.of(event));

        List<EventReportResponse> result = reportService.summarize("startAt");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).acceptedCount()).isEqualTo(2L);
        assertThat(result.get(0).fillRate()).isEqualTo(0.67);
    }

    // 正常系（UT-RPT-02）: sort=accepted_descなら受付済数の多い順に並ぶ
    @Test
    void summarize_正常系_accepted_descなら受付済数の多い順に並ぶ() {
        // Repositoryは開催日時順（受付済数は1→5→3）で返す状況を設定する
        Event first = event(1L, "受付1件", 10, 1L);
        Event second = event(2L, "受付5件", 10, 5L);
        Event third = event(3L, "受付3件", 10, 3L);
        when(eventRepository.findAllByDeletedAtIsNullOrderByStartAtAsc()).thenReturn(List.of(first, second, third));

        List<EventReportResponse> result = reportService.summarize("accepted_desc");

        // 受付済数の多い順（5→3→1）に並び替わることを確認する
        assertThat(result).extracting(EventReportResponse::acceptedCount).containsExactly(5L, 3L, 1L);
        // 既定の並び順（開催日時順）では、Repositoryが返した順のままであることも確認する
        assertThat(reportService.summarize("startAt")).extracting(EventReportResponse::eventId)
                .containsExactly(1L, 2L, 3L);
    }

    // 正常系（UT-RPT-03）: 集計対象は削除されていないイベントのみ
    // （削除済みの除外そのものはRepositoryのクエリが行うため、ここでは未削除のみを取得するメソッドを使うことを確認する）
    @Test
    void summarize_正常系_削除済みイベントは含まれない() {
        Event active = event(1L, "有効なイベント", 10, 0L);
        when(eventRepository.findAllByDeletedAtIsNullOrderByStartAtAsc()).thenReturn(List.of(active));

        List<EventReportResponse> result = reportService.summarize("startAt");

        // 未削除のみを取得するメソッドの結果だけが集計され、全件取得・削除済み取得は呼ばれないことを確認する
        assertThat(result).extracting(EventReportResponse::eventName).containsExactly("有効なイベント");
        verify(eventRepository, never()).findAll();
        verify(eventRepository, never()).findAllByDeletedAtIsNotNullOrderByStartAtAsc();
    }

    // 正常系（UT-RPT-04）: CSVは先頭がBOM、1行目が仕様どおりのヘッダ行になる
    @Test
    void toCsv_正常系_先頭がBOMでヘッダ行が仕様どおり() {
        Application application = application("説明会", "一般ユーザー", ApplicationStatus.ACCEPTED, "勉強のため", "午前");
        when(applicationRepository.findAllByEvent_DeletedAtIsNullOrderByEvent_StartAtAsc())
                .thenReturn(List.of(application));

        String csv = reportService.toCsv();

        // 先頭がBOMであることを確認する
        assertThat(csv).startsWith(BOM);
        String[] lines = csv.substring(BOM.length()).split("\r\n");
        // 1行目がヘッダ行、2行目が申込1件分の明細であることを確認する
        assertThat(lines[0]).isEqualTo(HEADER);
        assertThat(lines[1]).isEqualTo("説明会,一般ユーザー," + APPLIED_AT + ",受付済,勉強のため,午前");
        // 行末が\r\nで終わることを確認する
        assertThat(csv).endsWith("\r\n");
    }

    // 正常系（UT-RPT-05）: 受付済・キャンセル待ち・キャンセル済のすべての申込が出力される
    @Test
    void toCsv_正常系_すべてのステータスの申込が出力される() {
        String[] lines = csvLines(List.of(
                application("説明会", "利用者A", ApplicationStatus.ACCEPTED, null, null),
                application("説明会", "利用者B", ApplicationStatus.WAITLISTED, null, null),
                application("説明会", "利用者C", ApplicationStatus.CANCELLED, null, null)));

        // ヘッダ行＋3件が出力されることを確認する
        assertThat(lines).hasSize(4);
        // アンケート未回答・区分なしの列は空欄になることもあわせて確認する
        assertThat(lines[1]).isEqualTo("説明会,利用者A," + APPLIED_AT + ",受付済,,");
        assertThat(lines[2]).isEqualTo("説明会,利用者B," + APPLIED_AT + ",キャンセル待ち,,");
        assertThat(lines[3]).isEqualTo("説明会,利用者C," + APPLIED_AT + ",キャンセル済,,");
    }

    // 境界（UT-RPT-06）: カンマ・改行・ダブルクォートを含む値はダブルクォートで囲まれ、内部の"は""になる
    @Test
    void toCsv_境界_カンマ改行ダブルクォートを含む値はダブルクォートで囲まれる() {
        Application application = application("秋,祭り", "山田\"太郎\"", ApplicationStatus.ACCEPTED, "1行目\n2行目", null);
        when(applicationRepository.findAllByEvent_DeletedAtIsNullOrderByEvent_StartAtAsc())
                .thenReturn(List.of(application));

        // 値の中に改行を含むため、行分割せずヘッダ行より後ろの全文で確認する
        String body = reportService.toCsv().substring(BOM.length() + HEADER.length() + 2);

        assertThat(body).isEqualTo(
                "\"秋,祭り\",\"山田\"\"太郎\"\"\"," + APPLIED_AT + ",受付済,\"1行目\n2行目\",\r\n");
    }

    // 境界（UT-RPT-07）: 先頭が = + - @ タブ 復帰 の値には、先頭に'が付与される（CSVインジェクション対策）
    @Test
    void toCsv_境界_数式として解釈されうる先頭文字にはシングルクォートが付与される() {
        Application application = application("=SUM(1)", "+81", ApplicationStatus.ACCEPTED, "-1", "@午前");
        Application control = application("\tタブ始まり", "\r復帰始まり", ApplicationStatus.ACCEPTED, null, null);
        when(applicationRepository.findAllByEvent_DeletedAtIsNullOrderByEvent_StartAtAsc())
                .thenReturn(List.of(application, control));

        // 値の中に復帰文字を含むため、行分割せずヘッダ行より後ろの全文で確認する
        String body = reportService.toCsv().substring(BOM.length() + HEADER.length() + 2);

        assertThat(body).isEqualTo(
                "'=SUM(1),'+81," + APPLIED_AT + ",受付済,'-1,'@午前\r\n"
                        // 復帰文字を含む値は、'を付与したうえでダブルクォートで囲まれる
                        + "'\tタブ始まり,\"'\r復帰始まり\"," + APPLIED_AT + ",受付済,,\r\n");
    }

    // 対策対象外の値（先頭以外に記号がある・通常の文字で始まる）には'が付与されないことの確認（UT-RPT-07の補足）
    @Test
    void toCsv_境界_先頭以外の記号にはシングルクォートが付与されない() {
        String[] lines = csvLines(List.of(
                application("A=B", "a-b@example", ApplicationStatus.ACCEPTED, "1+1", null)));

        assertThat(lines[1]).isEqualTo("A=B,a-b@example," + APPLIED_AT + ",受付済,1+1,");
    }
}
