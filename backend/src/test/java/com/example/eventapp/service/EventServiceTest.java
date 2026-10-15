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

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.common.CurrentUser;
import com.example.eventapp.common.exception.BusinessException;
import com.example.eventapp.common.exception.NotFoundException;
import com.example.eventapp.dto.EventDetailResponse;
import com.example.eventapp.dto.EventUpsertRequest;
import com.example.eventapp.dto.TicketTypeRequest;
import com.example.eventapp.entity.ApplicationStatus;
import com.example.eventapp.entity.Event;
import com.example.eventapp.entity.RoleCode;
import com.example.eventapp.entity.TicketType;
import com.example.eventapp.repository.ApplicationRepository;
import com.example.eventapp.repository.EventRepository;
import com.example.eventapp.repository.FavoriteRepository;
import com.example.eventapp.repository.TicketTypeRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * EventService（イベントの作成・更新・削除・復元などの業務ロジック）に対する単体テスト。
 * JUnit5とMockitoを使用する。Repository（DBアクセスを担うクラス）はすべてモック化（偽装）し、
 * 本物のDBに接続せずに「申込があれば削除できない」「区分の定員合計にcapacityを同期する」等の
 * 業務ルールだけを検証する。
 */
// 実行環境: サーバー側（JVM）。G-1: EventService.delete()の業務ロジック（要件定義書§8）のユニットテスト。
// かつて「削除はカスケードされる」と誤認していたが、実際は受付済の申込が残っていると削除を拒否する
// 仕様であることが分かったため、そのことを回帰確認できるようテストとして残す。
// 機能追加（ソフトデリート）: delete()は物理削除ではなくdeleted_atを立てるのみになったため、
// softDelete()が呼ばれることと、listDeleted()/restore()の挙動を合わせて確認する。
// 機能追加（定員区分）: create()/update()での区分の全置換ロジックを検証する。
class EventServiceTest {

    private EventRepository eventRepository;
    private ApplicationRepository applicationRepository;
    private TicketTypeRepository ticketTypeRepository;
    private FavoriteRepository favoriteRepository;
    private AuthContext authContext;
    private EventService eventService;

    private static final Long EVENT_ID = 10L;

    // @BeforeEachが付いたメソッドは各@Testメソッドの実行前に毎回呼ばれ、テストごとに新しいモックとServiceを用意する。
    @BeforeEach
    void setUp() {
        // mock(クラス.class)で、本物のRepository（DBに接続するクラス）の代わりに
        // 振る舞いを偽装したオブジェクトを作る。テストメソッド内のwhen(...).thenReturn(...)で
        // 戻り値を設定し、DBに依存せずEventServiceの業務ロジックだけを検証する。
        eventRepository = mock(EventRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        ticketTypeRepository = mock(TicketTypeRepository.class);
        favoriteRepository = mock(FavoriteRepository.class);
        authContext = mock(AuthContext.class);
        // 操作ログ（docs/30_詳細設計/33_共通詳細設計書.md）出力のため、create/update/delete/restoreはログイン中管理者を参照する
        when(authContext.getCurrentUser()).thenReturn(new CurrentUser(2L, "管理者", RoleCode.ADMIN));
        // モック化したRepository・AuthContextを渡して、テスト対象のServiceを生成する
        eventService = new EventService(eventRepository, applicationRepository, ticketTypeRepository, favoriteRepository, authContext);
        // toDetail()が呼ばれる大半のテストで空の区分一覧を返す既定値にしておく
        when(ticketTypeRepository.findByEvent_Id(EVENT_ID)).thenReturn(List.of());
        // saveTicketTypes()が戻り値の区分一覧をcapacity合計に使うため、保存した引数をそのまま返す既定値にしておく
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // 正常系: 受付済の申込が無いイベントは削除（ソフトデリート）できる
    @Test
    void delete_正常系_申込が無ければ削除できる() {
        // モック化したEventを用意する（本物のEventは作らず、挙動だけ偽装する）
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        // 「対象イベントが存在し、削除もされていない」という状況を設定する
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        // 受付済の申込が0件（＝削除を妨げる申込が無い）という状況を設定する
        when(applicationRepository.countByEvent_IdAndStatus(EVENT_ID, ApplicationStatus.ACCEPTED)).thenReturn(0L);

        // テスト対象のメソッドを実行する
        eventService.delete(EVENT_ID);

        // 物理削除ではなく、ソフトデリート用のsoftDelete()が呼ばれたことを確認する
        verify(event).softDelete();
        // Repositoryの物理削除（delete）は呼ばれていないことを確認する
        verify(eventRepository, never()).delete(event);
    }

    // 異常系: 受付済の申込が1件でもあれば削除は拒否される（要件定義書§8 E5）。カスケード削除はしない
    @Test
    void delete_異常系_受付済の申込があれば削除できない() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        // 受付済の申込が1件ある（＝削除できない状況）を設定する
        when(applicationRepository.countByEvent_IdAndStatus(EVENT_ID, ApplicationStatus.ACCEPTED)).thenReturn(1L);

        // 削除を実行するとBusinessException（業務ルール違反）がスローされることを確認する
        assertThatThrownBy(() -> eventService.delete(EVENT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("申込があるため削除できません");
        // 拒否されたのでsoftDelete()は呼ばれていないことを確認する
        verify(event, never()).softDelete();
    }

    // 異常系: 存在しない（または既に削除済みの）イベントの削除は404相当
    @Test
    void delete_異常系_イベントが存在しなければNotFoundException() {
        // 対象イベントが見つからない状況を設定する
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.empty());

        // NotFoundExceptionがスローされることを確認する
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
        // 削除日時（deleted_at）を設定する（これが設定されていること＝削除済み）
        when(event.getDeletedAt()).thenReturn(LocalDateTime.of(2026, 9, 16, 12, 0));
        // 削除済み一覧を取得するRepositoryメソッドが、上で作った1件を返す状況を設定する
        when(eventRepository.findAllByDeletedAtIsNotNullOrderByStartAtAsc()).thenReturn(List.of(event));

        // テスト対象のメソッドを実行する
        var result = eventService.listDeleted();

        // 結果が1件であることを確認する
        assertThat(result).hasSize(1);
        // イベントIDが一致することを確認する
        assertThat(result.get(0).id()).isEqualTo(EVENT_ID);
        // 削除日時がそのまま反映されることを確認する
        assertThat(result.get(0).deletedAt()).isEqualTo(LocalDateTime.of(2026, 9, 16, 12, 0));
    }

    // 正常系: 削除済み一覧のレスポンスに、複製に必要な項目（説明・主催者名・区分等）が含まれる
    @Test
    void listDeleted_正常系_複製に必要な項目を含む() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getDescription()).thenReturn("説明文");
        when(event.getOrganizerName()).thenReturn("主催団体");
        when(event.getImageUrl()).thenReturn("https://example.com/image.png");
        when(event.getExtraQuestion()).thenReturn("参加動機を教えてください");
        when(eventRepository.findAllByDeletedAtIsNotNullOrderByStartAtAsc()).thenReturn(List.of(event));
        // このイベントに紐づく区分を1件用意する
        TicketType ticketType = mock(TicketType.class);
        when(ticketType.getName()).thenReturn("一般枠");
        when(ticketType.getCapacity()).thenReturn(5);
        // このイベントの区分一覧として、上で作った1件が返る状況を設定する
        when(ticketTypeRepository.findByEvent_Id(EVENT_ID)).thenReturn(List.of(ticketType));

        var result = eventService.listDeleted();

        // 説明・主催者名・画像URL・アンケート質問文が、それぞれ元データと一致することを確認する
        assertThat(result.get(0).description()).isEqualTo("説明文");
        assertThat(result.get(0).organizerName()).isEqualTo("主催団体");
        assertThat(result.get(0).imageUrl()).isEqualTo("https://example.com/image.png");
        assertThat(result.get(0).extraQuestion()).isEqualTo("参加動機を教えてください");
        // 区分一覧も1件含まれ、その区分名が一致することを確認する
        assertThat(result.get(0).ticketTypes()).hasSize(1);
        assertThat(result.get(0).ticketTypes().get(0).name()).isEqualTo("一般枠");
    }

    // 正常系: 削除済みイベントは復元できる
    @Test
    void restore_正常系_削除済みイベントを復元できる() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        // 対象イベントが「削除済み」として見つかる状況を設定する
        when(eventRepository.findByIdAndDeletedAtIsNotNull(EVENT_ID)).thenReturn(Optional.of(event));

        EventDetailResponse response = eventService.restore(EVENT_ID);

        // 復元用のrestore()メソッド（deleted_atをNULLに戻す処理）が呼ばれたことを確認する
        verify(event).restore();
        // レスポンスのIDが元のイベントIDと一致することを確認する
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
        // 2つの区分（合計定員40）を指定した登録リクエストを用意する
        EventUpsertRequest request = upsertRequestWithTicketTypes(
                new TicketTypeRequest("一般枠", 30),
                new TicketTypeRequest("会員枠", 10));
        Event savedEvent = mock(Event.class);
        when(savedEvent.getId()).thenReturn(EVENT_ID);
        // 新規保存すると上のEventが返る状況を設定する
        when(eventRepository.save(any(Event.class))).thenReturn(savedEvent);
        // 新規作成なので、保存前の既存区分は0件という状況を設定する
        when(ticketTypeRepository.findByEvent_Id(EVENT_ID)).thenReturn(List.of());

        // テスト対象のメソッド（登録処理）を実行する
        eventService.create(request);

        // 新規作成でも既存区分の削除（0件、無害）は同じロジックで呼ばれる。create/updateで処理を分けていないため
        verify(ticketTypeRepository).deleteByEvent_Id(EVENT_ID);
        // 区分を2件分（一般枠・会員枠）保存したことを確認する
        verify(ticketTypeRepository, times(2)).save(any(TicketType.class));
        // capacityが区分の定員合計（30+10=40）に同期されたことを確認する
        verify(savedEvent).syncCapacityFromTicketTypes(40);
    }

    // 正常系（要件定義書§8）: capacity未指定でも、区分を指定していれば区分の定員合計を暫定capacityとして登録できる
    @Test
    void create_正常系_capacity未指定でも区分の定員合計を使う() {
        // capacity（4番目の引数）をnullにし、区分だけを指定した登録リクエストを作る
        EventUpsertRequest request = new EventUpsertRequest(
                "テストイベント", LocalDateTime.now().plusDays(10), "会場", null,
                LocalDateTime.now().plusDays(5), "説明", "主催者", null, null,
                List.of(new TicketTypeRequest("一般枠", 30), new TicketTypeRequest("会員枠", 10)));
        when(ticketTypeRepository.findByEvent_Id(EVENT_ID)).thenReturn(List.of());
        // save()に実際に渡されたEventの内容を後から確認するためのキャプチャを用意する
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        Event savedEvent = mock(Event.class);
        when(savedEvent.getId()).thenReturn(EVENT_ID);
        when(eventRepository.save(eventCaptor.capture())).thenReturn(savedEvent);

        eventService.create(request);

        // save()に渡されたEventのcapacityが、区分の定員合計（30+10=40）になっていることを確認する
        assertThat(eventCaptor.getValue().getCapacity()).isEqualTo(40);
    }

    // 異常系（要件定義書§8）: capacityも区分も指定が無ければ登録できない（区分未選択時のApplicationServiceと同じ考え方）
    @Test
    void create_異常系_capacityも区分も未指定なら登録できない() {
        // capacityも区分（最後の引数）も指定しないリクエストを作る
        EventUpsertRequest request = new EventUpsertRequest(
                "テストイベント", LocalDateTime.now().plusDays(10), "会場", null,
                LocalDateTime.now().plusDays(5), "説明", "主催者", null, null, null);

        // 登録を実行するとBusinessExceptionがスローされることを確認する
        assertThatThrownBy(() -> eventService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("定員を入力してください");
        // 保存処理自体が呼ばれていないことを確認する
        verify(eventRepository, never()).save(any());
    }

    // 正常系（機能追加：定員区分）: ticketTypesを指定しない（null）場合は既存の区分に手を加えない
    @Test
    void update_正常系_区分未指定なら既存の区分は変更しない() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        when(ticketTypeRepository.findByEvent_Id(EVENT_ID)).thenReturn(List.of()); // 既存の区分なし
        // ticketTypesにnullを渡す（＝区分欄を変更しない、という意味の更新リクエスト）
        EventUpsertRequest request = upsertRequestWithTicketTypes((TicketTypeRequest[]) null);

        eventService.update(EVENT_ID, request);

        // 区分未指定なので、既存区分の削除・新規保存どちらも行われないことを確認する
        verify(ticketTypeRepository, never()).deleteByEvent_Id(EVENT_ID);
        verify(ticketTypeRepository, never()).save(any(TicketType.class));
        // capacityの同期処理も呼ばれないことを確認する
        verify(event, never()).syncCapacityFromTicketTypes(org.mockito.ArgumentMatchers.anyInt());
    }

    // 正常系（機能追加：定員区分）: 区分未指定でも既存の区分があれば、capacityをその合計値に保つ
    // （docs/20_基本設計/22_テーブル定義書.md「区分がある場合はcapacityは区分の合計」との矛盾を防ぐ）
    @Test
    void update_正常系_区分未指定でも既存区分があればcapacityを合計に保つ() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        // 既存の区分が2件（定員3＋2=5）ある状況を設定する
        when(ticketTypeRepository.findByEvent_Id(EVENT_ID)).thenReturn(List.of(
                new TicketType(event, "一般枠", 3),
                new TicketType(event, "会員枠", 2)));
        EventUpsertRequest request = upsertRequestWithTicketTypes((TicketTypeRequest[]) null);

        eventService.update(EVENT_ID, request);

        // 区分未指定なので削除は行われないことを確認する
        verify(ticketTypeRepository, never()).deleteByEvent_Id(EVENT_ID);
        // ただし既存区分の合計値（3+2=5）にcapacityが同期されることを確認する
        verify(event).syncCapacityFromTicketTypes(5);
    }

    // 正常系（要件定義書§8）: 更新時、capacity未指定・区分未指定（変更なし）でも既存区分があればそれを暫定capacityにする
    @Test
    void update_正常系_capacity未指定かつ区分未指定でも既存区分があれば維持される() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        when(ticketTypeRepository.findByEvent_Id(EVENT_ID)).thenReturn(List.of(
                new TicketType(event, "一般枠", 3),
                new TicketType(event, "会員枠", 2)));
        // capacity（4番目の引数）と区分（最後の引数）を両方nullにしたリクエストを作る
        EventUpsertRequest request = new EventUpsertRequest(
                "テストイベント", LocalDateTime.now().plusDays(10), "会場", null,
                LocalDateTime.now().plusDays(5), "説明", "主催者", null, null, null);

        eventService.update(EVENT_ID, request);

        // applyChanges()に渡されたcapacity引数を後から取り出して確認するためのキャプチャを用意する
        ArgumentCaptor<Integer> capacityCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(event).applyChanges(any(), any(), any(), capacityCaptor.capture(), any(), any(), any(), any(), any());
        // applyChanges()に渡されたcapacityが、既存区分の合計値（5）になっていることを確認する
        assertThat(capacityCaptor.getValue()).isEqualTo(5);
        // saveTicketTypes()側でも改めて合計値に同期される（既存の挙動どおり）
        verify(event).syncCapacityFromTicketTypes(5);
    }

    // 異常系（要件定義書§8）: capacity未指定・区分未指定（変更なし）で、既存区分も無ければ更新できない
    @Test
    void update_異常系_capacity未指定かつ既存区分も無ければ更新できない() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        // 既存区分も0件という状況を設定する
        when(ticketTypeRepository.findByEvent_Id(EVENT_ID)).thenReturn(List.of());
        EventUpsertRequest request = new EventUpsertRequest(
                "テストイベント", LocalDateTime.now().plusDays(10), "会場", null,
                LocalDateTime.now().plusDays(5), "説明", "主催者", null, null, null);

        // capacityも既存区分も無いため、更新するとBusinessExceptionがスローされることを確認する
        assertThatThrownBy(() -> eventService.update(EVENT_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("定員を入力してください");
        // 更新の本体処理（applyChanges）が呼ばれていないことを確認する
        verify(event, never()).applyChanges(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // 異常系（要件定義書§8）: capacity未指定で区分を空（全解除）にする場合は、区分が無くなるため更新できない
    @Test
    void update_異常系_capacity未指定で区分を空にすると更新できない() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        // 区分（最後の引数）に空リストを渡す（＝「全ての区分を解除する」という更新リクエスト）
        EventUpsertRequest request = new EventUpsertRequest(
                "テストイベント", LocalDateTime.now().plusDays(10), "会場", null,
                LocalDateTime.now().plusDays(5), "説明", "主催者", null, null, List.of());

        // 区分を空にするとcapacityの根拠が無くなるため、更新できないことを確認する
        assertThatThrownBy(() -> eventService.update(EVENT_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("定員を入力してください");
    }

    // 正常系（機能追加：定員区分）: 区分ありで更新すると、既存区分を削除してから新しい区分を保存する
    @Test
    void update_正常系_区分を全置換しcapacityが同期される() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        // 受付済・キャンセル待ちの申込は残っていない（＝区分を変更しても問題ない）状況を設定する
        when(applicationRepository.existsByEvent_IdAndStatusIn(
                EVENT_ID, ApplicationStatus.ACTIVE_STATUSES)).thenReturn(false);
        EventUpsertRequest request = upsertRequestWithTicketTypes(new TicketTypeRequest("一般枠", 20));

        eventService.update(EVENT_ID, request);

        // 既存区分が削除されたことを確認する（全置換の前半）
        verify(ticketTypeRepository).deleteByEvent_Id(EVENT_ID);
        // 新しい区分が保存されたことを確認する（全置換の後半）
        verify(ticketTypeRepository).save(any(TicketType.class));
        // capacityが新しい区分の定員（20）に同期されたことを確認する
        verify(event).syncCapacityFromTicketTypes(20);
    }

    // 異常系（機能追加：定員区分）: 区分に受付済・キャンセル待ちの申込が残っていれば変更を拒否する
    @Test
    void update_異常系_区分に申込が残っていれば変更できない() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        // 受付済・キャンセル待ちの申込が残っている状況を設定する
        when(applicationRepository.existsByEvent_IdAndStatusIn(
                EVENT_ID, ApplicationStatus.ACTIVE_STATUSES)).thenReturn(true);
        EventUpsertRequest request = upsertRequestWithTicketTypes(new TicketTypeRequest("一般枠", 20));

        // 申込が残っているため、区分変更を伴う更新はBusinessExceptionになることを確認する
        assertThatThrownBy(() -> eventService.update(EVENT_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("区分に申込があるため変更できません");
        // 事前チェックで止まるため、既存区分の削除は実行されていないことを確認する
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
        // 事前チェック（受付済・キャンセル待ちの有無）は通過する状況を設定する
        when(applicationRepository.existsByEvent_IdAndStatusIn(
                EVENT_ID, ApplicationStatus.ACTIVE_STATUSES)).thenReturn(false);
        // ticketTypeRepository.deleteByEvent_Id()が呼ばれた際に、DBのFK制約違反を模した例外を投げるよう設定する
        doThrow(new DataIntegrityViolationException("FK制約違反"))
                .when(ticketTypeRepository).deleteByEvent_Id(EVENT_ID);
        EventUpsertRequest request = upsertRequestWithTicketTypes(new TicketTypeRequest("一般枠", 20));

        // DB制約違反がそのままではなく、分かりやすいBusinessExceptionに変換されてスローされることを確認する
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
        // 区分の無いイベントだが、受付済・キャンセル待ちの申込が残っている状況を設定する
        when(applicationRepository.existsByEvent_IdAndStatusIn(EVENT_ID, ApplicationStatus.ACTIVE_STATUSES))
                .thenReturn(true);
        EventUpsertRequest request = upsertRequestWithTicketTypes(new TicketTypeRequest("一般枠", 20));

        assertThatThrownBy(() -> eventService.update(EVENT_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("区分に申込があるため変更できません");
        verify(ticketTypeRepository, never()).deleteByEvent_Id(EVENT_ID);
    }

    // 異常系: 同一イベント内で区分名が重複していれば変更を拒否する
    @Test
    void update_異常系_区分名が重複していれば変更できない() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        when(applicationRepository.existsByEvent_IdAndStatusIn(
                EVENT_ID, ApplicationStatus.ACTIVE_STATUSES)).thenReturn(false);
        // 同じ名前（一般枠）の区分を2つ指定したリクエストを作る
        EventUpsertRequest request = upsertRequestWithTicketTypes(
                new TicketTypeRequest("一般枠", 20), new TicketTypeRequest("一般枠", 10));

        // 区分名の重複によりBusinessExceptionがスローされることを確認する
        assertThatThrownBy(() -> eventService.update(EVENT_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("区分名が重複しています");
        // 重複チェックで止まるため、保存処理は呼ばれていないことを確認する
        verify(ticketTypeRepository, never()).save(any(TicketType.class));
    }

    // テスト用のEventUpsertRequestを組み立てるヘルパーメソッド。ticketTypesだけを可変にし、
    // 他の項目（名前・開催日時等）は固定値にすることで、各テストの記述量を減らしている。
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
                // 可変長引数ticketTypesがnullならnullを、そうでなければList化して渡す
                ticketTypes == null ? null : List.of(ticketTypes));
    }

    // 正常系（UT-EVT-03）: キャンセル待ち・キャンセル済の申込しか無ければ削除できる（削除を止めるのは受付済のみ）
    @Test
    void delete_正常系_キャンセル待ちとキャンセル済の申込のみなら削除できる() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        // 受付済は0件、キャンセル待ち2件・キャンセル済1件がある状況を設定する
        when(applicationRepository.countByEvent_IdAndStatus(EVENT_ID, ApplicationStatus.ACCEPTED)).thenReturn(0L);
        when(applicationRepository.countByEvent_IdAndStatus(EVENT_ID, ApplicationStatus.WAITLISTED)).thenReturn(2L);
        when(applicationRepository.countByEvent_IdAndStatus(EVENT_ID, ApplicationStatus.CANCELLED)).thenReturn(1L);

        eventService.delete(EVENT_ID);

        // 論理削除されることを確認する
        verify(event).softDelete();
        verify(eventRepository, never()).delete(event);
    }

    // 正常系（UT-EVT-22）: status=openなら、受付中かつ未削除のイベントのみを開催日時昇順で返す
    // （未削除の絞り込みと開催日時順の並びはRepositoryのクエリが行うため、ここではそのメソッドを使うことと、
    // 受付終了のイベントが除かれ、Repositoryが返した順が保たれることを確認する）
    @Test
    void list_正常系_openなら受付中かつ未削除のみを開催日時昇順で返す() {
        Event open1 = mock(Event.class);
        when(open1.getId()).thenReturn(1L);
        when(open1.isOpen(any(LocalDateTime.class))).thenReturn(true);
        // 受付終了（申込締切を過ぎた）イベント
        Event closed = mock(Event.class);
        when(closed.getId()).thenReturn(2L);
        when(closed.isOpen(any(LocalDateTime.class))).thenReturn(false);
        Event open2 = mock(Event.class);
        when(open2.getId()).thenReturn(3L);
        when(open2.isOpen(any(LocalDateTime.class))).thenReturn(true);
        // 未削除のイベントを開催日時昇順で取得するRepositoryメソッドが、3件をこの順で返す状況を設定する
        when(eventRepository.findAllByDeletedAtIsNullOrderByStartAtAsc()).thenReturn(List.of(open1, closed, open2));

        var result = eventService.list("open");

        // 受付終了の1件が除かれ、Repositoryが返した順（開催日時昇順）のまま返ることを確認する
        assertThat(result).extracting(summary -> summary.id()).containsExactly(1L, 3L);
        assertThat(result).allMatch(summary -> summary.open());
        // status=allでは受付終了のイベントも含めて3件返ることを確認する
        assertThat(eventService.list("all")).extracting(summary -> summary.id()).containsExactly(1L, 2L, 3L);
        // 削除済みを含む取得メソッドは使われないことを確認する
        verify(eventRepository, never()).findAll();
        verify(eventRepository, never()).findAllByDeletedAtIsNotNullOrderByStartAtAsc();
    }
}
