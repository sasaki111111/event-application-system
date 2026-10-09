package com.example.eventapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.common.CodeNameResolver;
import com.example.eventapp.common.CurrentUser;
import com.example.eventapp.common.exception.BusinessException;
import com.example.eventapp.common.exception.ForbiddenException;
import com.example.eventapp.common.exception.NotFoundException;
import com.example.eventapp.dto.ApplicationResponse;
import com.example.eventapp.dto.AttendeeResponse;
import com.example.eventapp.dto.CheckInResponse;
import com.example.eventapp.dto.MyApplicationResponse;
import com.example.eventapp.entity.Application;
import com.example.eventapp.entity.ApplicationStatus;
import com.example.eventapp.entity.Event;
import com.example.eventapp.entity.RoleCode;
import com.example.eventapp.entity.TicketType;
import com.example.eventapp.entity.User;
import com.example.eventapp.repository.ApplicationRepository;
import com.example.eventapp.repository.EventRepository;
import com.example.eventapp.repository.TicketTypeRepository;
import com.example.eventapp.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * ApplicationService（申込の登録・取消・申込者一覧・チェックインなどの業務ロジック）に対する単体テスト。
 * JUnit5とMockitoを使用する。Repository（DBアクセスを担うクラス）は本物を使わずすべてモック化（偽装）し、
 * 「このメソッドが呼ばれたらこの値を返す」という振る舞いだけを設定することで、実際のDBに接続せずに
 * 業務ロジック（定員判定・キャンセル待ちへの繰り上げ等）だけを検証する。
 */
// 実行環境: サーバー側（JVM）。G-1: ApplicationServiceの業務ロジック（要件定義書§8＋機能追加のキャンセル待ち・定員区分）のユニットテスト。
// JUnit5＋Mockito。Repositoryは全てモック化し、DBに触れずにビジネスロジックだけを検証する。
class ApplicationServiceTest {

    private ApplicationRepository applicationRepository;
    private EventRepository eventRepository;
    private UserRepository userRepository;
    private TicketTypeRepository ticketTypeRepository;
    private AuthContext authContext;
    private ApplicationService applicationService;

    private static final Long USER_ID = 1L;
    private static final Long EVENT_ID = 10L;

    // @BeforeEachが付いたメソッドは各@Testメソッドの実行前に毎回呼ばれ、テストごとに新しいモックとServiceを用意する。
    @BeforeEach
    void setUp() {
        // mock(クラス.class) で、本物の代わりに「振る舞いを偽装したオブジェクト（モック）」を作る。
        // 何も設定しなければ呼び出しても何もしない（メソッドは既定値を返す）ため、
        // 下の when(...).thenReturn(...) で「このメソッドが呼ばれたらこの値を返す」という振る舞いを後から設定する。
        // これにより、ApplicationRepository（本物はDBに接続する）の実装に依存せずにApplicationServiceだけを検証できる。
        applicationRepository = mock(ApplicationRepository.class);
        eventRepository = mock(EventRepository.class);
        userRepository = mock(UserRepository.class);
        ticketTypeRepository = mock(TicketTypeRepository.class);
        authContext = mock(AuthContext.class);
        // 操作ログ（docs/30_詳細設計/33_共通詳細設計書.md）出力のため、checkIn()はログイン中管理者を参照する
        when(authContext.getCurrentUser()).thenReturn(new CurrentUser(2L, "管理者", RoleCode.ADMIN));
        // モック化したRepository・AuthContextを渡して、テスト対象のServiceを生成する
        applicationService = new ApplicationService(
                applicationRepository, eventRepository, userRepository, ticketTypeRepository, authContext,
                codeNameResolver());
        // 区分の無いイベントを既定値にしておく（区分ありのテストでは個別にstubし直す）
        when(ticketTypeRepository.existsByEvent_Id(EVENT_ID)).thenReturn(false);
    }

    // テスト用の「定員capacity・受付中」なEventのモックを組み立てるヘルパーメソッド。
    // 各テストで同じ設定を書かずに済むようにしている。
    private Event openEvent(int capacity) {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getCapacity()).thenReturn(capacity);
        // isOpen(現在時刻)がtrueを返す＝申込締切・開催日時をまだ過ぎていない状態にする
        when(event.isOpen(any(LocalDateTime.class))).thenReturn(true);
        return event;
    }

    // 正常系: 定員に余裕があり、未申込、受付中のイベントなら「受付済」で申込が成功する
    @Test
    void apply_正常系_定員に余裕があれば受付済で申込できる() {
        // 定員5・受付中のイベントを用意する
        Event event = openEvent(5);
        // 対象イベントが存在する状況を設定する
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        // 受付済の申込数0件（＝定員に余裕がある）という状況を設定する
        when(applicationRepository.countByEvent_IdAndStatus(EVENT_ID, ApplicationStatus.ACCEPTED)).thenReturn(0L);
        // まだこのユーザーがこのイベントに申込んでいない（二重申込ではない）状況を設定する
        when(applicationRepository.existsByUser_IdAndEvent_IdAndStatusIn(anyLong(), anyLong(), any()))
                .thenReturn(false);
        User user = mock(User.class);
        // 申込者のユーザーIDからUserの参照を取得する処理を偽装する
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);

        // ArgumentCaptorは、モックのメソッド（ここではsave）に実際に渡された引数を後から取り出して検証するための仕組み。
        // 戻り値を設定するwhen(...).thenReturn(...)と同時に使うことで、「saveに渡されたApplicationの内容」を
        // captor.getValue()で確認できるようになる。
        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        // save()が呼ばれたときに返す「保存済みの申込」を用意する
        Application saved = new Application(user, event);
        when(applicationRepository.save(captor.capture())).thenReturn(saved);

        // テスト対象のメソッド（申込処理）を実行する
        ApplicationResponse response = applicationService.apply(USER_ID, EVENT_ID, null, null);

        // レスポンスのイベントIDが一致することを確認する
        assertThat(response.eventId()).isEqualTo(EVENT_ID);
        // レスポンスのユーザーIDが一致することを確認する
        assertThat(response.userId()).isEqualTo(USER_ID);
        // 定員に余裕があるため、ステータスが「受付済」になることを確認する
        assertThat(response.statusCode()).isEqualTo(ApplicationStatus.ACCEPTED);
        // save()に渡されたApplicationの申込者が、期待したユーザーと一致することを確認する
        assertThat(captor.getValue().getUser()).isEqualTo(user);
    }

    // 異常系: 存在しないイベントへの申込は404相当の例外
    @Test
    void apply_異常系_イベントが存在しなければNotFoundException() {
        // 対象イベントが見つからない状況を設定する
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.empty());

        // 申込を実行するとNotFoundExceptionがスローされることを確認する
        assertThatThrownBy(() -> applicationService.apply(USER_ID, EVENT_ID, null, null))
                .isInstanceOf(NotFoundException.class);
        // イベントが見つからない時点で処理を止めるため、applicationRepositoryへは一切アクセスしていないことを確認する
        verifyNoInteractions(applicationRepository);
    }

    // 異常系: 申込締切または開催日時を過ぎたイベントへの申込は拒否される（要件定義書§8 E2）
    @Test
    void apply_異常系_受付終了のイベントには申込できない() {
        Event closedEvent = mock(Event.class);
        // isOpen()がfalseを返す＝受付終了（申込締切または開催日時を過ぎている）状態にする
        when(closedEvent.isOpen(any(LocalDateTime.class))).thenReturn(false);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(closedEvent));

        assertThatThrownBy(() -> applicationService.apply(USER_ID, EVENT_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("申込受付は終了しました");
        verifyNoInteractions(applicationRepository);
    }

    // 機能追加: 受付済の申込数が定員に達しているイベントに申込むと、エラーにはならず「キャンセル待ち」で登録される
    @Test
    void apply_機能追加_定員に達していればキャンセル待ちで登録される() {
        // 定員3のイベントを用意する
        Event fullEvent = openEvent(3);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(fullEvent));
        // 受付済の申込数がちょうど定員と同じ3件（＝満員）という状況を設定する
        when(applicationRepository.countByEvent_IdAndStatus(EVENT_ID, ApplicationStatus.ACCEPTED)).thenReturn(3L);
        when(applicationRepository.existsByUser_IdAndEvent_IdAndStatusIn(anyLong(), anyLong(), any()))
                .thenReturn(false);
        User user = mock(User.class);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        // save()が呼ばれたら、渡された引数（保存しようとしたApplication）をそのまま返すよう設定する
        when(applicationRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationResponse response = applicationService.apply(USER_ID, EVENT_ID, null, null);

        // 満員のため、エラーにはならず「キャンセル待ち」になることを確認する
        assertThat(response.statusCode()).isEqualTo(ApplicationStatus.WAITLISTED);
        // save()に渡されたApplication自体のステータスも「キャンセル待ち」になっていることを確認する
        assertThat(captor.getValue().getStatus()).isEqualTo(ApplicationStatus.WAITLISTED);
    }

    // 異常系: 同一ユーザーが同一イベントにすでに「受付済」で申し込んでいれば二重申込は拒否される（要件定義書§8 E3）
    @Test
    void apply_異常系_二重申込はできない() {
        Event event = openEvent(5);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        // すでに「受付済」または「キャンセル待ち」で申込済みという状況を設定する
        when(applicationRepository.existsByUser_IdAndEvent_IdAndStatusIn(anyLong(), anyLong(), any()))
                .thenReturn(true);

        // 二重申込によりBusinessExceptionがスローされることを確認する
        assertThatThrownBy(() -> applicationService.apply(USER_ID, EVENT_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("すでに申し込み済みです");
        // 拒否されたのでsave()は呼ばれていないことを確認する
        verify(applicationRepository, never()).save(any());
    }

    // 機能追加: すでに「キャンセル待ち」で申し込んでいる場合も二重申込は拒否される
    @Test
    void apply_機能追加_キャンセル待ち中の二重申込もできない() {
        Event event = openEvent(1);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        when(applicationRepository.existsByUser_IdAndEvent_IdAndStatusIn(anyLong(), anyLong(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> applicationService.apply(USER_ID, EVENT_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("すでに申し込み済みです");
    }

    // 正常系: 受付済かつ開催日時前の申込は取消できる（要件定義書§8）
    @Test
    void cancel_正常系_受付済かつ開催前なら取消できる() {
        // 申込者となるUserをモック化する
        User owner = mock(User.class);
        // 取消を実行する本人（USER_ID）が申込者と同じになるよう設定する
        when(owner.getId()).thenReturn(USER_ID);
        Event futureEvent = mock(Event.class);
        // 開催日時を未来（1日後）に設定する＝まだ開催前
        when(futureEvent.getStartAt()).thenReturn(LocalDateTime.now().plusDays(1));
        // 本物のApplicationエンティティを1件生成する（既定のステータスは「受付済」）
        Application application = new Application(owner, futureEvent);
        // 取消対象の申込IDで、この申込が見つかる状況を設定する
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        // テスト対象のメソッド（取消処理）を実行する
        applicationService.cancel(USER_ID, 100L);

        // 申込のステータスが「キャンセル済」に変わったことを確認する
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
    }

    // 機能追加: 受付済をキャンセルすると、最も古いキャンセル待ちが自動的に受付済へ繰り上がる
    @Test
    void cancel_機能追加_受付済のキャンセルでキャンセル待ちが繰り上がる() {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(USER_ID);
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getStartAt()).thenReturn(LocalDateTime.now().plusDays(1));
        Application application = new Application(owner, event);
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        // 繰り上げ対象となる、キャンセル待ちの別ユーザーの申込を用意する
        User waitingUser = mock(User.class);
        Application waitlisted = new Application(waitingUser, event, ApplicationStatus.WAITLISTED);
        // 「このイベントで区分指定無し・キャンセル待ちの中で最も古い申込」として、上の申込が見つかる状況を設定する
        when(applicationRepository.findFirstByEvent_IdAndTicketTypeIsNullAndStatusOrderByAppliedAtAsc(
                EVENT_ID, ApplicationStatus.WAITLISTED)).thenReturn(Optional.of(waitlisted));

        applicationService.cancel(USER_ID, 100L);

        // 取消した申込がキャンセル済になることを確認する
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        // 繰り上げ対象だったキャンセル待ちの申込が、受付済に変わったことを確認する
        assertThat(waitlisted.getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
    }

    // 機能追加: キャンセル待ちの申込自体も取消できる（繰り上げは発生しない＝枠が空いていないため）
    @Test
    void cancel_機能追加_キャンセル待ちの申込も取消できる() {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(USER_ID);
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getStartAt()).thenReturn(LocalDateTime.now().plusDays(1));
        // 取消対象の申込自体を「キャンセル待ち」状態で作る
        Application application = new Application(owner, event, ApplicationStatus.WAITLISTED);
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        applicationService.cancel(USER_ID, 100L);

        // キャンセル済になることを確認する
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        // 元が「キャンセル待ち」の取消（枠が空いたわけではない）なので、繰り上げ処理が呼ばれていないことを確認する
        verify(applicationRepository, never())
                .findFirstByEvent_IdAndTicketTypeIsNullAndStatusOrderByAppliedAtAsc(anyLong(), any());
    }

    // 異常系: 他人の申込は取消できない（403相当）
    @Test
    void cancel_異常系_他人の申込は取消できない() {
        User owner = mock(User.class);
        // 申込者のIDを、取消を実行する本人（USER_ID=1）とは異なる999に設定する
        when(owner.getId()).thenReturn(999L);
        Event futureEvent = mock(Event.class);
        Application application = new Application(owner, futureEvent);
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        // 他人の申込を取消しようとするとForbiddenException（403相当）がスローされることを確認する
        assertThatThrownBy(() -> applicationService.cancel(USER_ID, 100L))
                .isInstanceOf(ForbiddenException.class);
        // 拒否されたので、申込のステータスは変わっていない（受付済のまま）ことを確認する
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
    }

    // 異常系: すでにキャンセル済み、または開催日時を過ぎた申込は取消できない（要件定義書§8 E4）
    @Test
    void cancel_異常系_開催日時を過ぎた申込は取消できない() {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(USER_ID);
        Event pastEvent = mock(Event.class);
        // 開催日時を過去（1日前）に設定する＝もう開催が終わっている
        when(pastEvent.getStartAt()).thenReturn(LocalDateTime.now().minusDays(1));
        Application application = new Application(owner, pastEvent);
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        // 開催済みの申込を取消しようとするとBusinessExceptionがスローされることを確認する
        assertThatThrownBy(() -> applicationService.cancel(USER_ID, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("取消できません");
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
    }

    // 異常系: 存在しない申込IDの取消は404相当
    @Test
    void cancel_異常系_申込が存在しなければNotFoundException() {
        // 取消対象の申込が見つからない状況を設定する
        when(applicationRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.cancel(USER_ID, 100L))
                .isInstanceOf(NotFoundException.class);
    }

    // テスト用の「ID・定員capacity」を持つTicketType（参加区分）のモックを組み立てるヘルパーメソッド
    private TicketType ticketType(Long id, int capacity) {
        TicketType ticketType = mock(TicketType.class);
        when(ticketType.getId()).thenReturn(id);
        when(ticketType.getCapacity()).thenReturn(capacity);
        return ticketType;
    }

    // 正常系（機能追加：定員区分）: 区分の定員に余裕があれば、その区分の受付済数で判定し受付済になる
    @Test
    void apply_機能追加_区分の定員に余裕があれば受付済で申込できる() {
        // イベント全体の定員は999（十分大きい）だが、区分（定員2）の方で判定されることを確認するテスト
        Event event = openEvent(999);
        TicketType ticketType = ticketType(1L, 2);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        // このイベントには区分が存在する状況を設定する
        when(ticketTypeRepository.existsByEvent_Id(EVENT_ID)).thenReturn(true);
        // 指定した区分ID(1L)が、このイベントの区分として見つかる状況を設定する（forUpdate＝悲観ロック付き取得）
        when(ticketTypeRepository.findByIdAndEvent_IdForUpdate(1L, EVENT_ID)).thenReturn(Optional.of(ticketType));
        // この区分の受付済数が1件（定員2に対して余裕がある）という状況を設定する
        when(applicationRepository.countByTicketType_IdAndStatus(1L, ApplicationStatus.ACCEPTED)).thenReturn(1L);
        when(applicationRepository.existsByUser_IdAndEvent_IdAndStatusIn(anyLong(), anyLong(), any()))
                .thenReturn(false);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(mock(User.class));
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // 区分ID(1L)を指定して申込む
        ApplicationResponse response = applicationService.apply(USER_ID, EVENT_ID, 1L, null);

        // 区分の定員に余裕があるため受付済になることを確認する
        assertThat(response.statusCode()).isEqualTo(ApplicationStatus.ACCEPTED);
        // レスポンスの区分IDが指定したものと一致することを確認する
        assertThat(response.ticketTypeId()).isEqualTo(1L);
        // 区分がある場合はイベント全体の受付済数（countByEvent_IdAndStatus）では判定しないことを確認する
        verify(applicationRepository, never()).countByEvent_IdAndStatus(anyLong(), any());
    }

    // 機能追加（定員区分）: 区分の定員に達していれば、イベント全体に空きがあってもその区分はキャンセル待ちになる
    @Test
    void apply_機能追加_区分の定員に達していればキャンセル待ちで登録される() {
        Event event = openEvent(999);
        TicketType ticketType = ticketType(1L, 2);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        when(ticketTypeRepository.existsByEvent_Id(EVENT_ID)).thenReturn(true);
        when(ticketTypeRepository.findByIdAndEvent_IdForUpdate(1L, EVENT_ID)).thenReturn(Optional.of(ticketType));
        // この区分の受付済数が、区分の定員（2）とちょうど同じ＝区分だけ満員という状況を設定する
        when(applicationRepository.countByTicketType_IdAndStatus(1L, ApplicationStatus.ACCEPTED)).thenReturn(2L);
        when(applicationRepository.existsByUser_IdAndEvent_IdAndStatusIn(anyLong(), anyLong(), any()))
                .thenReturn(false);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(mock(User.class));
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationResponse response = applicationService.apply(USER_ID, EVENT_ID, 1L, null);

        // イベント全体（999）には余裕があっても、区分単位で判定されキャンセル待ちになることを確認する
        assertThat(response.statusCode()).isEqualTo(ApplicationStatus.WAITLISTED);
    }

    // 異常系（機能追加：定員区分）: 区分があるイベントで区分未指定は400（要件定義書§8 E12）
    @Test
    void apply_異常系_区分があるのに未指定なら区分を選択してくださいで拒否される() {
        Event event = openEvent(999);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        when(ticketTypeRepository.existsByEvent_Id(EVENT_ID)).thenReturn(true);
        when(applicationRepository.existsByUser_IdAndEvent_IdAndStatusIn(anyLong(), anyLong(), any()))
                .thenReturn(false);

        // 区分が必要なイベントなのに、区分ID（3番目の引数）をnullにして申込もうとする
        assertThatThrownBy(() -> applicationService.apply(USER_ID, EVENT_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("区分を選択してください");
        verify(applicationRepository, never()).save(any());
    }

    // 異常系（機能追加：定員区分）: 対象イベントに属さない区分の指定は404（要件定義書§8 E11）
    @Test
    void apply_異常系_存在しない区分を指定すると指定された区分が見つかりませんで拒否される() {
        Event event = openEvent(999);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        when(ticketTypeRepository.existsByEvent_Id(EVENT_ID)).thenReturn(true);
        // 指定された区分ID(99L)はこのイベントには存在しない状況を設定する
        when(ticketTypeRepository.findByIdAndEvent_IdForUpdate(99L, EVENT_ID)).thenReturn(Optional.empty());
        when(applicationRepository.existsByUser_IdAndEvent_IdAndStatusIn(anyLong(), anyLong(), any()))
                .thenReturn(false);

        // 存在しない区分ID(99L)を指定して申込もうとする
        assertThatThrownBy(() -> applicationService.apply(USER_ID, EVENT_ID, 99L, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("指定された区分が見つかりません");
        verify(applicationRepository, never()).save(any());
    }

    // 異常系（機能追加：定員区分）: 区分の無いイベントに区分を指定した場合も404（32_処理詳細設計書 4章 AP-030 No.7）
    @Test
    void apply_異常系_区分の無いイベントに区分を指定すると指定された区分が見つかりませんで拒否される() {
        Event event = openEvent(999);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        // このイベントには参加区分が1件も無い状況を設定する
        when(ticketTypeRepository.existsByEvent_Id(EVENT_ID)).thenReturn(false);
        when(applicationRepository.existsByUser_IdAndEvent_IdAndStatusIn(anyLong(), anyLong(), any()))
                .thenReturn(false);

        // 区分の無いイベントに、区分ID(1L)を指定して申込もうとする
        assertThatThrownBy(() -> applicationService.apply(USER_ID, EVENT_ID, 1L, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("指定された区分が見つかりません");
        verify(applicationRepository, never()).save(any());
    }

    // 機能追加（定員区分）: 区分ありの申込をキャンセルすると、同じ区分で最も古いキャンセル待ちが繰り上がる
    @Test
    void cancel_機能追加_区分単位でキャンセル待ちが繰り上がる() {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(USER_ID);
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getStartAt()).thenReturn(LocalDateTime.now().plusDays(1));
        TicketType ticketType = ticketType(1L, 2);
        // 取消対象の申込を、区分付き・受付済の状態で作る
        Application application = new Application(owner, event, ticketType, ApplicationStatus.ACCEPTED, null);
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        User waitingUser = mock(User.class);
        // 同じ区分でキャンセル待ちの別ユーザーの申込を用意する（繰り上げ対象）
        Application waitlisted = new Application(waitingUser, event, ticketType, ApplicationStatus.WAITLISTED, null);
        // 「この区分でキャンセル待ちの中で最も古い申込」として、上の申込が見つかる状況を設定する
        when(applicationRepository.findFirstByTicketType_IdAndStatusOrderByAppliedAtAsc(1L, ApplicationStatus.WAITLISTED))
                .thenReturn(Optional.of(waitlisted));

        applicationService.cancel(USER_ID, 100L);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        // 同じ区分のキャンセル待ちが受付済に繰り上がったことを確認する
        assertThat(waitlisted.getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
        // 区分単位で繰り上げたので、区分指定無し用の繰り上げ検索は呼ばれていないことを確認する
        verify(applicationRepository, never())
                .findFirstByEvent_IdAndTicketTypeIsNullAndStatusOrderByAppliedAtAsc(anyLong(), any());
    }

    // 正常系（機能追加：定員区分）: 自分の申込一覧でキャンセル待ちの順位が区分単位で計算される
    @Test
    void myApplications_機能追加_区分単位でキャンセル待ちの順位が計算される() {
        User user = mock(User.class);
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getName()).thenReturn("テストイベント");
        when(event.getStartAt()).thenReturn(LocalDateTime.now().plusDays(1));
        TicketType ticketType = ticketType(1L, 2);
        // 区分付き・キャンセル待ちの申込を1件用意する
        Application application = new Application(user, event, ticketType, ApplicationStatus.WAITLISTED, null);
        // このユーザーの申込一覧として、上の1件が返る状況を設定する
        when(applicationRepository.findByUser_IdOrderByAppliedAtDesc(USER_ID)).thenReturn(List.of(application));
        // 自分より先に申込んだ（＝自分より順位が上の）同区分のキャンセル待ちが2件ある状況を設定する
        when(applicationRepository.countByTicketType_IdAndStatusAndAppliedAtLessThan(
                1L, ApplicationStatus.WAITLISTED, application.getAppliedAt())).thenReturn(2L);

        List<MyApplicationResponse> result = applicationService.myApplications(USER_ID);

        assertThat(result).hasSize(1);
        // 先に並んでいる2件＋自分自身で、繰り上がり順位は3番目になることを確認する
        assertThat(result.get(0).waitlistRank()).isEqualTo(3L);
    }

    // 正常系: キャンセル待ち以外の申込はwaitlistRankがNULLになる
    @Test
    void myApplications_正常系_受付済の申込はwaitlistRankがNULL() {
        User user = mock(User.class);
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getName()).thenReturn("テストイベント");
        when(event.getStartAt()).thenReturn(LocalDateTime.now().plusDays(1));
        // 既定のステータス（受付済）の申込を1件用意する
        Application application = new Application(user, event);
        when(applicationRepository.findByUser_IdOrderByAppliedAtDesc(USER_ID)).thenReturn(List.of(application));

        List<MyApplicationResponse> result = applicationService.myApplications(USER_ID);

        // 受付済（キャンセル待ちではない）ため、waitlistRankはnullになることを確認する
        assertThat(result.get(0).waitlistRank()).isNull();
        // キャンセル待ちではないので、順位計算用の集計処理が呼ばれていないことを確認する
        verify(applicationRepository, never())
                .countByEvent_IdAndTicketTypeIsNullAndStatusAndAppliedAtLessThan(anyLong(), any(), any());
    }

    // 正常系（機能追加：当日受付）: 申込者一覧が申込日時昇順で返り、区分名・チェックイン状況を含む
    @Test
    void listAttendees_正常系_申込者一覧を返す() {
        Event event = mock(Event.class);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        User user = mock(User.class);
        when(user.getName()).thenReturn("参加者A");
        TicketType ticketType = mock(TicketType.class);
        when(ticketType.getName()).thenReturn("一般枠");
        // Applicationそのものをモック化し、各getterの戻り値を個別に設定する
        Application application = mock(Application.class);
        when(application.getUser()).thenReturn(user);
        when(application.getTicketType()).thenReturn(ticketType);
        when(application.getStatus()).thenReturn(ApplicationStatus.ACCEPTED);
        // 申込時アンケートの回答内容を設定する
        when(application.getExtraAnswer()).thenReturn("会場までは電車で向かいます");
        // このイベントの申込者一覧（申込日時の昇順）として、上の1件が返る状況を設定する
        when(applicationRepository.findByEvent_IdOrderByAppliedAtAsc(EVENT_ID)).thenReturn(List.of(application));

        List<AttendeeResponse> result = applicationService.listAttendees(EVENT_ID);

        assertThat(result).hasSize(1);
        // 参加者名が反映されることを確認する
        assertThat(result.get(0).userName()).isEqualTo("参加者A");
        // 区分名が反映されることを確認する
        assertThat(result.get(0).ticketTypeName()).isEqualTo("一般枠");
        // まだチェックインしていないのでnullであることを確認する
        assertThat(result.get(0).checkedInAt()).isNull();
        // （機能追加）: 申込時アンケートへの回答が当日受付一覧にも含まれる
        assertThat(result.get(0).extraAnswer()).isEqualTo("会場までは電車で向かいます");
    }

    // 正常系（機能追加：当日受付）: 区分の無いイベントの申込はticketTypeNameがNULL
    @Test
    void listAttendees_正常系_区分が無ければticketTypeNameはNULL() {
        Event event = mock(Event.class);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        User user = mock(User.class);
        // 区分を指定しない（通常の）申込を1件用意する
        Application application = new Application(user, event);
        when(applicationRepository.findByEvent_IdOrderByAppliedAtAsc(EVENT_ID)).thenReturn(List.of(application));

        List<AttendeeResponse> result = applicationService.listAttendees(EVENT_ID);

        // 区分が無いのでticketTypeNameはnullであることを確認する
        assertThat(result.get(0).ticketTypeName()).isNull();
        // （機能追加）: アンケート未回答（コンストラクタ既定値NULL）の場合はextraAnswerもNULL
        assertThat(result.get(0).extraAnswer()).isNull();
    }

    // 異常系: 存在しないイベントの申込者一覧取得は404相当
    @Test
    void listAttendees_異常系_イベントが存在しなければNotFoundException() {
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.listAttendees(EVENT_ID))
                .isInstanceOf(NotFoundException.class);
    }

    // 正常系（機能追加：当日受付）: 受付済の申込はチェックインできる
    @Test
    void checkIn_正常系_受付済の申込はチェックインできる() {
        User user = mock(User.class);
        Event event = mock(Event.class);
        // 既定のステータス（受付済）の申込を1件用意する
        Application application = new Application(user, event);
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        // テスト対象のメソッド（チェックイン処理）を実行する
        CheckInResponse response = applicationService.checkIn(100L);

        // チェックイン日時が設定された（nullではなくなった）ことを確認する
        assertThat(application.getCheckedInAt()).isNotNull();
        // レスポンスのチェックイン日時が、申込に設定された値と一致することを確認する
        assertThat(response.checkedInAt()).isEqualTo(application.getCheckedInAt());
    }

    // 異常系（R-06）: キャンセル待ちの申込はチェックインできない
    @Test
    void checkIn_異常系_キャンセル待ちの申込はチェックインできない() {
        User user = mock(User.class);
        Event event = mock(Event.class);
        // 申込状況を「キャンセル待ち」にした申込を用意する（受付済ではない）
        Application application = new Application(user, event, ApplicationStatus.WAITLISTED);
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        // 受付済以外はチェックインできないため、BusinessExceptionがスローされることを確認する
        assertThatThrownBy(() -> applicationService.checkIn(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("受付済の申込のみチェックインできます");
        // 拒否されたので、チェックイン日時は設定されていない（nullのまま）ことを確認する
        assertThat(application.getCheckedInAt()).isNull();
    }

    // 異常系（R-06）: キャンセル済の申込はチェックインできない
    @Test
    void checkIn_異常系_キャンセル済の申込はチェックインできない() {
        User user = mock(User.class);
        Event event = mock(Event.class);
        // 申込状況を「キャンセル済」にした申込を用意する
        Application application = new Application(user, event, ApplicationStatus.CANCELLED);
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> applicationService.checkIn(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("受付済の申込のみチェックインできます");
        assertThat(application.getCheckedInAt()).isNull();
    }

    // 異常系: 存在しない申込のチェックインは404相当
    @Test
    void checkIn_異常系_申込が存在しなければNotFoundException() {
        when(applicationRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.checkIn(100L))
                .isInstanceOf(NotFoundException.class);
    }

    // 正常系（UT-APP-07）: キャンセル済の申込しか無いイベントには再申込できる
    @Test
    void apply_正常系_キャンセル済の申込のみなら再申込できる() {
        Event event = openEvent(5);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        when(applicationRepository.countByEvent_IdAndStatus(EVENT_ID, ApplicationStatus.ACCEPTED)).thenReturn(0L);
        // 二重申込の判定は有効な申込（受付済・キャンセル待ち）だけが対象。
        // キャンセル済の申込しか無いので、有効な申込は「無い」という状況を設定する
        when(applicationRepository.existsByUser_IdAndEvent_IdAndStatusIn(
                USER_ID, EVENT_ID, ApplicationStatus.ACTIVE_STATUSES)).thenReturn(false);
        User user = mock(User.class);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(applicationRepository.save(any(Application.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationResponse response = applicationService.apply(USER_ID, EVENT_ID, null, null);

        // エラーにならず、新しい申込が受付済で登録されることを確認する
        assertThat(response.statusCode()).isEqualTo(ApplicationStatus.ACCEPTED);
        verify(applicationRepository).save(any(Application.class));
        // 二重申込の判定対象にキャンセル済が含まれていないことを確認する
        verify(applicationRepository).existsByUser_IdAndEvent_IdAndStatusIn(
                USER_ID, EVENT_ID, ApplicationStatus.ACTIVE_STATUSES);
        assertThat(ApplicationStatus.ACTIVE_STATUSES).doesNotContain(ApplicationStatus.CANCELLED);
    }

    // 境界（UT-APP-12）: 現在時刻が申込締切ちょうどなら受付中（締切「以前」は受付）と判定される。
    // apply()は現在時刻をLocalDateTime.now()で取得するため時刻を固定できない。そこで、apply()が受付可否の判定に
    // 使っているEvent#isOpen(現在時刻)に、固定した時刻を直接渡して境界の挙動を確認する。
    @Test
    void apply_境界_現在時刻が申込締切ちょうどなら受付中と判定される() {
        LocalDateTime deadline = LocalDateTime.of(2026, 11, 15, 23, 59, 0);
        LocalDateTime startAt = LocalDateTime.of(2026, 11, 20, 10, 0, 0);
        // モックではなく本物のEventを使い、実際の判定ロジックを動かす
        Event event = new Event("境界確認イベント", startAt, "会場", 5, deadline, null, null, null, null);

        // 締切の1秒前・締切ちょうどは受付中
        assertThat(event.isOpen(deadline.minusSeconds(1))).isTrue();
        assertThat(event.isOpen(deadline)).isTrue();
        // 締切を1秒でも過ぎたら受付終了
        assertThat(event.isOpen(deadline.plusSeconds(1))).isFalse();
    }

    // 異常系（UT-APP-19）: キャンセル済の申込は再度取消できない
    @Test
    void cancel_異常系_キャンセル済の申込は取消できない() {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(USER_ID);
        Event futureEvent = mock(Event.class);
        // 開催前であっても、キャンセル済であれば取消できないことを確認するため開催日時は未来にする
        when(futureEvent.getStartAt()).thenReturn(LocalDateTime.now().plusDays(1));
        Application application = new Application(owner, futureEvent, ApplicationStatus.CANCELLED);
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> applicationService.cancel(USER_ID, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("取消できません");
        // キャンセル待ちの繰り上げが発生しないことを確認する
        verify(applicationRepository, never())
                .findFirstByEvent_IdAndTicketTypeIsNullAndStatusOrderByAppliedAtAsc(anyLong(), any());
    }

    // 正常系（UT-APP-27）: チェックイン済みの申込に再度チェックインすると、日時が最新の実行時刻に更新される
    @Test
    void checkIn_正常系_チェックイン済みに再実行すると日時が更新される() throws InterruptedException {
        User user = mock(User.class);
        Event event = mock(Event.class);
        Application application = new Application(user, event);
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        // 1回目のチェックイン
        LocalDateTime first = applicationService.checkIn(100L).checkedInAt();
        // 2回目との時刻差を確実に作るため少し待つ
        Thread.sleep(20);
        // 2回目のチェックイン（エラーにならない）
        CheckInResponse second = applicationService.checkIn(100L);

        // チェックイン日時が1回目より後の時刻に更新されていることを確認する
        assertThat(second.checkedInAt()).isAfter(first);
        assertThat(application.getCheckedInAt()).isEqualTo(second.checkedInAt());
    }

    // 区分値の表示名（コードマスタの内容）を返すCodeNameResolverのモックを組み立てるヘルパーメソッド。
    // 単体テストではDBに接続しないため、コードマスタの初期データと同じ対応をここで定義する
    private static CodeNameResolver codeNameResolver() {
        CodeNameResolver resolver = mock(CodeNameResolver.class);
        when(resolver.roleName(RoleCode.GENERAL)).thenReturn("一般利用者");
        when(resolver.roleName(RoleCode.ADMIN)).thenReturn("管理者");
        when(resolver.statusName(ApplicationStatus.ACCEPTED)).thenReturn("受付済");
        when(resolver.statusName(ApplicationStatus.WAITLISTED)).thenReturn("キャンセル待ち");
        when(resolver.statusName(ApplicationStatus.CANCELLED)).thenReturn("キャンセル済");
        return resolver;
    }
}
