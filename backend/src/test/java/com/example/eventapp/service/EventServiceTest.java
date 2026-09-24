package com.example.eventapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.eventapp.common.exception.BusinessException;
import com.example.eventapp.common.exception.NotFoundException;
import com.example.eventapp.dto.EventDetailResponse;
import com.example.eventapp.dto.EventUpsertRequest;
import com.example.eventapp.dto.TicketTypeRequest;
import com.example.eventapp.entity.ApplicationStatus;
import com.example.eventapp.entity.Event;
import com.example.eventapp.entity.TicketType;
import com.example.eventapp.repository.ApplicationRepository;
import com.example.eventapp.repository.EventRepository;
import com.example.eventapp.repository.TicketTypeRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

// 実行環境: サーバー側（JVM）。G-1: EventService.delete()の業務ロジック（要件定義書§8）のユニットテスト。
// D-8時点で「削除はカスケードされる」と誤認していたが、実際は受付済の申込が残っていると削除を拒否する
// 仕様であることが分かったため、そのことを回帰確認できるようテストとして残す。
// 機能追加（ソフトデリート）: delete()は物理削除ではなくdeleted_atを立てるのみになったため、
// softDelete()が呼ばれることと、listDeleted()/restore()の挙動を合わせて確認する。
// 機能追加（定員区分）: create()/update()での区分の全置換ロジックを検証する。
class EventServiceTest {

    private EventRepository eventRepository;
    private ApplicationRepository applicationRepository;
    private TicketTypeRepository ticketTypeRepository;
    private EventService eventService;

    private static final Long EVENT_ID = 10L;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        ticketTypeRepository = mock(TicketTypeRepository.class);
        eventService = new EventService(eventRepository, applicationRepository, ticketTypeRepository);
        // toDetail()が呼ばれる大半のテストで空の区分一覧を返す既定値にしておく
        when(ticketTypeRepository.findByEvent_Id(EVENT_ID)).thenReturn(List.of());
        // saveTicketTypes()が戻り値の区分一覧をcapacity合計に使うため、保存した引数をそのまま返す既定値にしておく
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // 正常系: 受付済の申込が無いイベントは削除（ソフトデリート）できる
    @Test
    void delete_正常系_申込が無ければ削除できる() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        when(applicationRepository.countByEvent_IdAndStatus(EVENT_ID, ApplicationStatus.ACCEPTED)).thenReturn(0L);

        eventService.delete(EVENT_ID);

        verify(event).softDelete();
        verify(eventRepository, never()).delete(event);
    }

    // 異常系: 受付済の申込が1件でもあれば削除は拒否される（要件定義書§8 E5）。カスケード削除はしない
    @Test
    void delete_異常系_受付済の申込があれば削除できない() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        when(applicationRepository.countByEvent_IdAndStatus(EVENT_ID, ApplicationStatus.ACCEPTED)).thenReturn(1L);

        assertThatThrownBy(() -> eventService.delete(EVENT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("申込があるため削除できません");
        verify(event, never()).softDelete();
    }

    // 異常系: 存在しない（または既に削除済みの）イベントの削除は404相当
    @Test
    void delete_異常系_イベントが存在しなければNotFoundException() {
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.delete(EVENT_ID))
                .isInstanceOf(NotFoundException.class);
    }

    // 正常系: 削除済み一覧はdeleted_atがある行のみを返す
    @Test
    void listDeleted_正常系_削除済みイベントのみ返す() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getName()).thenReturn("削除されたイベント");
        when(event.getStartAt()).thenReturn(LocalDateTime.of(2026, 10, 1, 10, 0));
        when(event.getPlace()).thenReturn("会場");
        when(event.getCapacity()).thenReturn(10);
        when(event.getDeletedAt()).thenReturn(LocalDateTime.of(2026, 9, 16, 12, 0));
        when(eventRepository.findAllByDeletedAtIsNotNullOrderByStartAtAsc()).thenReturn(List.of(event));

        var result = eventService.listDeleted();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(EVENT_ID);
        assertThat(result.get(0).deletedAt()).isEqualTo(LocalDateTime.of(2026, 9, 16, 12, 0));
    }

    // 正常系: 削除済みイベントは復元できる
    @Test
    void restore_正常系_削除済みイベントを復元できる() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(eventRepository.findByIdAndDeletedAtIsNotNull(EVENT_ID)).thenReturn(Optional.of(event));

        EventDetailResponse response = eventService.restore(EVENT_ID);

        verify(event).restore();
        assertThat(response.id()).isEqualTo(EVENT_ID);
    }

    // 異常系: 削除済みでない（＝存在しないか、既に有効な）イベントの復元は404相当
    @Test
    void restore_異常系_削除済みイベントが存在しなければNotFoundException() {
        when(eventRepository.findByIdAndDeletedAtIsNotNull(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.restore(EVENT_ID))
                .isInstanceOf(NotFoundException.class);
    }

    // 正常系（機能追加：定員区分）: 登録時にticketTypesを指定すると、区分が保存されcapacityが合計値に同期される
    @Test
    void create_正常系_区分を指定すると保存されcapacityが同期される() {
        EventUpsertRequest request = upsertRequestWithTicketTypes(
                new TicketTypeRequest("一般枠", 30),
                new TicketTypeRequest("会員枠", 10));
        Event savedEvent = mock(Event.class);
        when(savedEvent.getId()).thenReturn(EVENT_ID);
        when(eventRepository.save(any(Event.class))).thenReturn(savedEvent);
        when(ticketTypeRepository.findByEvent_Id(EVENT_ID)).thenReturn(List.of());

        eventService.create(request);

        // 新規作成でも既存区分の削除（0件、無害）は同じロジックで呼ばれる。create/updateで処理を分けていないため
        verify(ticketTypeRepository).deleteByEvent_Id(EVENT_ID);
        verify(ticketTypeRepository, times(2)).save(any(TicketType.class));
        verify(savedEvent).syncCapacityFromTicketTypes(40);
    }

    // 正常系（機能追加：定員区分）: ticketTypesを指定しない（null）場合は既存の区分に手を加えない
    @Test
    void update_正常系_区分未指定なら既存の区分は変更しない() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        when(ticketTypeRepository.findByEvent_Id(EVENT_ID)).thenReturn(List.of()); // 既存の区分なし
        EventUpsertRequest request = upsertRequestWithTicketTypes((TicketTypeRequest[]) null);

        eventService.update(EVENT_ID, request);

        verify(ticketTypeRepository, never()).deleteByEvent_Id(EVENT_ID);
        verify(ticketTypeRepository, never()).save(any(TicketType.class));
        verify(event, never()).syncCapacityFromTicketTypes(org.mockito.ArgumentMatchers.anyInt());
    }

    // 正常系（機能追加：定員区分）: 区分未指定でも既存の区分があれば、capacityをその合計値に保つ
    // （テーブル定義書_v2.0.md§2.2「区分がある場合はcapacityは区分の合計」との矛盾を防ぐ）
    @Test
    void update_正常系_区分未指定でも既存区分があればcapacityを合計に保つ() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        when(ticketTypeRepository.findByEvent_Id(EVENT_ID)).thenReturn(List.of(
                new TicketType(event, "一般枠", 3),
                new TicketType(event, "会員枠", 2)));
        EventUpsertRequest request = upsertRequestWithTicketTypes((TicketTypeRequest[]) null);

        eventService.update(EVENT_ID, request);

        verify(ticketTypeRepository, never()).deleteByEvent_Id(EVENT_ID);
        verify(event).syncCapacityFromTicketTypes(5);
    }

    // 正常系（機能追加：定員区分）: 区分ありで更新すると、既存区分を削除してから新しい区分を保存する
    @Test
    void update_正常系_区分を全置換しcapacityが同期される() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        when(applicationRepository.existsByEvent_IdAndStatusIn(
                EVENT_ID, ApplicationStatus.ACTIVE_STATUSES)).thenReturn(false);
        EventUpsertRequest request = upsertRequestWithTicketTypes(new TicketTypeRequest("一般枠", 20));

        eventService.update(EVENT_ID, request);

        verify(ticketTypeRepository).deleteByEvent_Id(EVENT_ID);
        verify(ticketTypeRepository).save(any(TicketType.class));
        verify(event).syncCapacityFromTicketTypes(20);
    }

    // 異常系（機能追加：定員区分）: 区分に受付済・キャンセル待ちの申込が残っていれば変更を拒否する
    @Test
    void update_異常系_区分に申込が残っていれば変更できない() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        when(applicationRepository.existsByEvent_IdAndStatusIn(
                EVENT_ID, ApplicationStatus.ACTIVE_STATUSES)).thenReturn(true);
        EventUpsertRequest request = upsertRequestWithTicketTypes(new TicketTypeRequest("一般枠", 20));

        assertThatThrownBy(() -> eventService.update(EVENT_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("区分に申込があるため変更できません");
        verify(ticketTypeRepository, never()).deleteByEvent_Id(EVENT_ID);
    }

    // 異常系（機能追加：定員区分）: キャンセル済の申込が区分を参照したまま残っている場合、
    // 事前チェック（受付済・キャンセル待ちのみ判定）をすり抜けてDB制約（RESTRICT）違反になるが、
    // 500ではなく分かりやすいBusinessExceptionに変換する
    @Test
    void update_異常系_キャンセル済の申込が区分を参照していれば変更できない() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        when(applicationRepository.existsByEvent_IdAndStatusIn(
                EVENT_ID, ApplicationStatus.ACTIVE_STATUSES)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("FK制約違反"))
                .when(ticketTypeRepository).deleteByEvent_Id(EVENT_ID);
        EventUpsertRequest request = upsertRequestWithTicketTypes(new TicketTypeRequest("一般枠", 20));

        assertThatThrownBy(() -> eventService.update(EVENT_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("区分に申込の履歴が残っているため変更できません");
    }

    // 異常系（機能追加：定員区分）: 区分の無いイベントに初めて区分を追加しようとしても、
    // 既存の申込（ticketTypeがNULL）が受付済・キャンセル待ちで残っていれば変更を拒否する。
    // これを許すと、既存の申込がどの区分にも属さないまま区分単位の定員判定・繰り上げから
    // 漏れてしまう（code-review PR#4で指摘）
    @Test
    void update_異常系_区分の無いイベントでも申込が残っていれば区分を追加できない() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        when(applicationRepository.existsByEvent_IdAndStatusIn(EVENT_ID, ApplicationStatus.ACTIVE_STATUSES))
                .thenReturn(true);
        EventUpsertRequest request = upsertRequestWithTicketTypes(new TicketTypeRequest("一般枠", 20));

        assertThatThrownBy(() -> eventService.update(EVENT_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("区分に申込があるため変更できません");
        verify(ticketTypeRepository, never()).deleteByEvent_Id(EVENT_ID);
    }

    private EventUpsertRequest upsertRequestWithTicketTypes(TicketTypeRequest... ticketTypes) {
        return new EventUpsertRequest(
                "テストイベント",
                LocalDateTime.now().plusDays(10),
                "会場",
                50,
                LocalDateTime.now().plusDays(5),
                "説明",
                "主催者",
                null,
                null,
                ticketTypes == null ? null : List.of(ticketTypes));
    }
}
