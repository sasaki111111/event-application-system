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

// 実行環境: サーバー側（JVM）。G-1: ApplicationServiceの業務ロジック（要件定義書§8＋機能追加のキャンセル待ち・定員区分）のユニットテスト。
// JUnit5＋Mockito。Repositoryは全てモック化し、DBに触れずにビジネスロジックだけを検証する。
class ApplicationServiceTest {

    private ApplicationRepository applicationRepository;
    private EventRepository eventRepository;
    private UserRepository userRepository;
    private TicketTypeRepository ticketTypeRepository;
    private ApplicationService applicationService;

    private static final Long USER_ID = 1L;
    private static final Long EVENT_ID = 10L;

    @BeforeEach
    void setUp() {
        applicationRepository = mock(ApplicationRepository.class);
        eventRepository = mock(EventRepository.class);
        userRepository = mock(UserRepository.class);
        ticketTypeRepository = mock(TicketTypeRepository.class);
        applicationService = new ApplicationService(
                applicationRepository, eventRepository, userRepository, ticketTypeRepository);
        // 区分の無いイベントを既定値にしておく（区分ありのテストでは個別にstubし直す）
        when(ticketTypeRepository.existsByEvent_Id(EVENT_ID)).thenReturn(false);
    }

    private Event openEvent(int capacity) {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getCapacity()).thenReturn(capacity);
        when(event.isOpen(any(LocalDateTime.class))).thenReturn(true);
        return event;
    }

    // 正常系: 定員に余裕があり、未申込、受付中のイベントなら「受付済」で申込が成功する
    @Test
    void apply_正常系_定員に余裕があれば受付済で申込できる() {
        Event event = openEvent(5);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        when(applicationRepository.countByEvent_IdAndStatus(EVENT_ID, ApplicationStatus.ACCEPTED)).thenReturn(0L);
        when(applicationRepository.existsByUser_IdAndEvent_IdAndStatusIn(anyLong(), anyLong(), any()))
                .thenReturn(false);
        User user = mock(User.class);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        Application saved = new Application(user, event);
        when(applicationRepository.save(captor.capture())).thenReturn(saved);

        ApplicationResponse response = applicationService.apply(USER_ID, EVENT_ID, null, null);

        assertThat(response.eventId()).isEqualTo(EVENT_ID);
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.status()).isEqualTo(ApplicationStatus.ACCEPTED);
        assertThat(captor.getValue().getUser()).isEqualTo(user);
    }

    // 異常系: 存在しないイベントへの申込は404相当の例外
    @Test
    void apply_異常系_イベントが存在しなければNotFoundException() {
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.apply(USER_ID, EVENT_ID, null, null))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(applicationRepository);
    }

    // 異常系: 申込締切または開催日時を過ぎたイベントへの申込は拒否される（要件定義書§8 E2）
    @Test
    void apply_異常系_受付終了のイベントには申込できない() {
        Event closedEvent = mock(Event.class);
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
        Event fullEvent = openEvent(3);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(fullEvent));
        when(applicationRepository.countByEvent_IdAndStatus(EVENT_ID, ApplicationStatus.ACCEPTED)).thenReturn(3L);
        when(applicationRepository.existsByUser_IdAndEvent_IdAndStatusIn(anyLong(), anyLong(), any()))
                .thenReturn(false);
        User user = mock(User.class);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        when(applicationRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationResponse response = applicationService.apply(USER_ID, EVENT_ID, null, null);

        assertThat(response.status()).isEqualTo(ApplicationStatus.WAITLISTED);
        assertThat(captor.getValue().getStatus()).isEqualTo(ApplicationStatus.WAITLISTED);
    }

    // 異常系: 同一ユーザーが同一イベントにすでに「受付済」で申し込んでいれば二重申込は拒否される（要件定義書§8 E3）
    @Test
    void apply_異常系_二重申込はできない() {
        Event event = openEvent(5);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        when(applicationRepository.existsByUser_IdAndEvent_IdAndStatusIn(anyLong(), anyLong(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> applicationService.apply(USER_ID, EVENT_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("すでに申し込み済みです");
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
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(USER_ID);
        Event futureEvent = mock(Event.class);
        when(futureEvent.getStartAt()).thenReturn(LocalDateTime.now().plusDays(1));
        Application application = new Application(owner, futureEvent);
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        applicationService.cancel(USER_ID, 100L);

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

        User waitingUser = mock(User.class);
        Application waitlisted = new Application(waitingUser, event, ApplicationStatus.WAITLISTED);
        when(applicationRepository.findFirstByEvent_IdAndTicketTypeIsNullAndStatusOrderByAppliedAtAsc(
                EVENT_ID, ApplicationStatus.WAITLISTED)).thenReturn(Optional.of(waitlisted));

        applicationService.cancel(USER_ID, 100L);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
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
        Application application = new Application(owner, event, ApplicationStatus.WAITLISTED);
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        applicationService.cancel(USER_ID, 100L);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        verify(applicationRepository, never())
                .findFirstByEvent_IdAndTicketTypeIsNullAndStatusOrderByAppliedAtAsc(anyLong(), any());
    }

    // 異常系: 他人の申込は取消できない（403相当）
    @Test
    void cancel_異常系_他人の申込は取消できない() {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(999L);
        Event futureEvent = mock(Event.class);
        Application application = new Application(owner, futureEvent);
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> applicationService.cancel(USER_ID, 100L))
                .isInstanceOf(ForbiddenException.class);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
    }

    // 異常系: すでにキャンセル済み、または開催日時を過ぎた申込は取消できない（要件定義書§8 E4）
    @Test
    void cancel_異常系_開催日時を過ぎた申込は取消できない() {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(USER_ID);
        Event pastEvent = mock(Event.class);
        when(pastEvent.getStartAt()).thenReturn(LocalDateTime.now().minusDays(1));
        Application application = new Application(owner, pastEvent);
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> applicationService.cancel(USER_ID, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("取消できません");
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
    }

    // 異常系: 存在しない申込IDの取消は404相当
    @Test
    void cancel_異常系_申込が存在しなければNotFoundException() {
        when(applicationRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.cancel(USER_ID, 100L))
                .isInstanceOf(NotFoundException.class);
    }

    private TicketType ticketType(Long id, int capacity) {
        TicketType ticketType = mock(TicketType.class);
        when(ticketType.getId()).thenReturn(id);
        when(ticketType.getCapacity()).thenReturn(capacity);
        return ticketType;
    }

    // 正常系（機能追加：定員区分）: 区分の定員に余裕があれば、その区分の受付済数で判定し受付済になる
    @Test
    void apply_機能追加_区分の定員に余裕があれば受付済で申込できる() {
        Event event = openEvent(999);
        TicketType ticketType = ticketType(1L, 2);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        when(ticketTypeRepository.existsByEvent_Id(EVENT_ID)).thenReturn(true);
        when(ticketTypeRepository.findByIdAndEvent_IdForUpdate(1L, EVENT_ID)).thenReturn(Optional.of(ticketType));
        when(applicationRepository.countByTicketType_IdAndStatus(1L, ApplicationStatus.ACCEPTED)).thenReturn(1L);
        when(applicationRepository.existsByUser_IdAndEvent_IdAndStatusIn(anyLong(), anyLong(), any()))
                .thenReturn(false);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(mock(User.class));
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationResponse response = applicationService.apply(USER_ID, EVENT_ID, 1L, null);

        assertThat(response.status()).isEqualTo(ApplicationStatus.ACCEPTED);
        assertThat(response.ticketTypeId()).isEqualTo(1L);
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
        when(applicationRepository.countByTicketType_IdAndStatus(1L, ApplicationStatus.ACCEPTED)).thenReturn(2L);
        when(applicationRepository.existsByUser_IdAndEvent_IdAndStatusIn(anyLong(), anyLong(), any()))
                .thenReturn(false);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(mock(User.class));
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationResponse response = applicationService.apply(USER_ID, EVENT_ID, 1L, null);

        assertThat(response.status()).isEqualTo(ApplicationStatus.WAITLISTED);
    }

    // 異常系（機能追加：定員区分）: 区分があるイベントで区分未指定は400（要件定義書§8 E12）
    @Test
    void apply_異常系_区分があるのに未指定なら区分を選択してくださいで拒否される() {
        Event event = openEvent(999);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        when(ticketTypeRepository.existsByEvent_Id(EVENT_ID)).thenReturn(true);
        when(applicationRepository.existsByUser_IdAndEvent_IdAndStatusIn(anyLong(), anyLong(), any()))
                .thenReturn(false);

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
        when(ticketTypeRepository.findByIdAndEvent_IdForUpdate(99L, EVENT_ID)).thenReturn(Optional.empty());
        when(applicationRepository.existsByUser_IdAndEvent_IdAndStatusIn(anyLong(), anyLong(), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> applicationService.apply(USER_ID, EVENT_ID, 99L, null))
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
        Application application = new Application(owner, event, ticketType, ApplicationStatus.ACCEPTED, null);
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        User waitingUser = mock(User.class);
        Application waitlisted = new Application(waitingUser, event, ticketType, ApplicationStatus.WAITLISTED, null);
        when(applicationRepository.findFirstByTicketType_IdAndStatusOrderByAppliedAtAsc(1L, ApplicationStatus.WAITLISTED))
                .thenReturn(Optional.of(waitlisted));

        applicationService.cancel(USER_ID, 100L);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        assertThat(waitlisted.getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
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
        Application application = new Application(user, event, ticketType, ApplicationStatus.WAITLISTED, null);
        when(applicationRepository.findByUser_IdOrderByAppliedAtDesc(USER_ID)).thenReturn(List.of(application));
        when(applicationRepository.countByTicketType_IdAndStatusAndAppliedAtLessThan(
                1L, ApplicationStatus.WAITLISTED, application.getAppliedAt())).thenReturn(2L);

        List<MyApplicationResponse> result = applicationService.myApplications(USER_ID);

        assertThat(result).hasSize(1);
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
        Application application = new Application(user, event);
        when(applicationRepository.findByUser_IdOrderByAppliedAtDesc(USER_ID)).thenReturn(List.of(application));

        List<MyApplicationResponse> result = applicationService.myApplications(USER_ID);

        assertThat(result.get(0).waitlistRank()).isNull();
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
        Application application = mock(Application.class);
        when(application.getUser()).thenReturn(user);
        when(application.getTicketType()).thenReturn(ticketType);
        when(application.getStatus()).thenReturn(ApplicationStatus.ACCEPTED);
        when(applicationRepository.findByEvent_IdOrderByAppliedAtAsc(EVENT_ID)).thenReturn(List.of(application));

        List<AttendeeResponse> result = applicationService.listAttendees(EVENT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userName()).isEqualTo("参加者A");
        assertThat(result.get(0).ticketTypeName()).isEqualTo("一般枠");
        assertThat(result.get(0).checkedInAt()).isNull();
    }

    // 正常系（機能追加：当日受付）: 区分の無いイベントの申込はticketTypeNameがNULL
    @Test
    void listAttendees_正常系_区分が無ければticketTypeNameはNULL() {
        Event event = mock(Event.class);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        User user = mock(User.class);
        Application application = new Application(user, event);
        when(applicationRepository.findByEvent_IdOrderByAppliedAtAsc(EVENT_ID)).thenReturn(List.of(application));

        List<AttendeeResponse> result = applicationService.listAttendees(EVENT_ID);

        assertThat(result.get(0).ticketTypeName()).isNull();
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
        Application application = new Application(user, event);
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        CheckInResponse response = applicationService.checkIn(100L);

        assertThat(application.getCheckedInAt()).isNotNull();
        assertThat(response.checkedInAt()).isEqualTo(application.getCheckedInAt());
    }

    // 異常系（要件定義書§8 E8）: 受付済以外（キャンセル待ち・キャンセル済）の申込はチェックインできない
    @Test
    void checkIn_異常系_受付済以外はチェックインできない() {
        User user = mock(User.class);
        Event event = mock(Event.class);
        Application application = new Application(user, event, ApplicationStatus.WAITLISTED);
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
}
