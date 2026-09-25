package com.example.eventapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.eventapp.dto.ApplicationCreateRequest;
import com.example.eventapp.dto.ApplicationResponse;
import com.example.eventapp.dto.EventDetailResponse;
import com.example.eventapp.dto.EventSummaryResponse;
import com.example.eventapp.dto.EventUpsertRequest;
import com.example.eventapp.dto.MyApplicationResponse;
import com.example.eventapp.dto.UserRegisterRequest;
import com.example.eventapp.dto.UserResponse;
import com.example.eventapp.entity.Application;
import com.example.eventapp.entity.ApplicationStatus;
import com.example.eventapp.entity.Event;
import com.example.eventapp.repository.ApplicationRepository;
import com.example.eventapp.repository.EventRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

// 実行環境: サーバー側（JVM）。G-2: API/DB結合テスト。
// Controller→Service→Repository→H2（インメモリDB）まで実際に通し、必須API 8本のうち代表7本を検証する。
// Userは公開コンストラクタが無いためJdbcTemplateで直接INSERTし、Eventは実際のEventRepositoryで作成する。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ApiIntegrationTest {

    private static final Long GENERAL_USER_ID = 1L;
    private static final Long ADMIN_USER_ID = 2L;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        eventRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, role, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                GENERAL_USER_ID, "一般ユーザー", "general@example.com", "general");
        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, role, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                ADMIN_USER_ID, "管理者", "admin@example.com", "admin");
    }

    private Event openEvent() {
        return openEvent(5);
    }

    private Event openEvent(int capacity) {
        return eventRepository.save(new Event(
                "結合テスト用イベント",
                LocalDateTime.now().plusDays(10),
                "会議室",
                capacity,
                LocalDateTime.now().plusDays(5),
                "G-2結合テスト用データ",
                null, null, null));
    }

    private HttpHeaders authHeaders(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(userId));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    // API-01: 登録したイベントがDBから読み出されて一覧に含まれる
    @Test
    void api01_イベント一覧取得() {
        Event event = openEvent();

        ResponseEntity<EventSummaryResponse[]> response = restTemplate.exchange(
                url("/api/events"), HttpMethod.GET, new HttpEntity<>(authHeaders(GENERAL_USER_ID)),
                EventSummaryResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(EventSummaryResponse::id).contains(event.getId());
    }

    // API-02: 指定したイベントの詳細がDBの内容通りに返る
    @Test
    void api02_イベント詳細取得() {
        Event event = openEvent();

        ResponseEntity<EventDetailResponse> response = restTemplate.exchange(
                url("/api/events/" + event.getId()), HttpMethod.GET, new HttpEntity<>(authHeaders(GENERAL_USER_ID)),
                EventDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("結合テスト用イベント");
        assertThat(response.getBody().remaining()).isEqualTo(5);
    }

    // API-06: 管理者が登録したイベントが実際にDBへ保存される
    @Test
    void api06_管理者はイベントを登録できる() {
        EventUpsertRequest request = new EventUpsertRequest(
                "新規登録テスト", LocalDateTime.now().plusDays(20), "会議室B", 10,
                LocalDateTime.now().plusDays(15), null, null, null, null, null);

        ResponseEntity<EventDetailResponse> response = restTemplate.exchange(
                url("/api/events"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(ADMIN_USER_ID)),
                EventDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long createdId = response.getBody().id();
        assertThat(eventRepository.findById(createdId)).isPresent();
        assertThat(eventRepository.findById(createdId).get().getName()).isEqualTo("新規登録テスト");
    }

    // API-06の権限チェック: 一般ユーザーは登録できず、DBにも作られない
    @Test
    void api06_一般ユーザーはイベントを登録できない() {
        EventUpsertRequest request = new EventUpsertRequest(
                "権限チェック用", LocalDateTime.now().plusDays(20), "会議室B", 10,
                LocalDateTime.now().plusDays(15), null, null, null, null, null);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/events"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(GENERAL_USER_ID)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(eventRepository.count()).isZero();
    }

    // API-03: 申込がDBに実際に1件作成される
    @Test
    void api03_一般ユーザーは申込できる() {
        Event event = openEvent();
        ApplicationCreateRequest request = new ApplicationCreateRequest(event.getId(), null, null);

        ResponseEntity<ApplicationResponse> response = restTemplate.exchange(
                url("/api/applications"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(GENERAL_USER_ID)),
                ApplicationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(applicationRepository.count()).isEqualTo(1);
    }

    // API-04: 自分が申し込んだ内容がDBから読み出されて一覧に反映される
    @Test
    void api04_自分の申込一覧取得() {
        Event event = openEvent();
        ApplicationCreateRequest applyRequest = new ApplicationCreateRequest(event.getId(), null, null);
        restTemplate.exchange(url("/api/applications"), HttpMethod.POST,
                new HttpEntity<>(applyRequest, authHeaders(GENERAL_USER_ID)), ApplicationResponse.class);

        ResponseEntity<MyApplicationResponse[]> response = restTemplate.exchange(
                url("/api/my/applications"), HttpMethod.GET, new HttpEntity<>(authHeaders(GENERAL_USER_ID)),
                MyApplicationResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].eventId()).isEqualTo(event.getId());
    }

    // API-05: キャンセル後、DB上のステータスが実際に更新される
    @Test
    void api05_申込をキャンセルできる() {
        Event event = openEvent();
        ResponseEntity<ApplicationResponse> applyResponse = restTemplate.exchange(
                url("/api/applications"), HttpMethod.POST,
                new HttpEntity<>(new ApplicationCreateRequest(event.getId(), null, null), authHeaders(GENERAL_USER_ID)),
                ApplicationResponse.class);
        Long applicationId = applyResponse.getBody().id();

        ResponseEntity<Void> cancelResponse = restTemplate.exchange(
                url("/api/applications/" + applicationId), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), Void.class);

        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        Application cancelled = applicationRepository.findById(applicationId).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo("キャンセル済");
    }

    // O-01: 同時申込時の排他制御（悲観ロック）。定員1のイベントに2人が同時に申込んでも、
    // 「受付済」になるのは1件だけで、もう1件は「キャンセル待ち」になる（二重受付が起きない）ことを確認する。
    @Test
    void o01_同時に申し込んでも定員を超えて受付済にならない() throws Exception {
        Event event = openEvent(1); // 定員1
        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, role, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                3L, "利用者3", "user3@example.com", "general");
        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, role, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                4L, "利用者4", "user4@example.com", "general");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<ResponseEntity<ApplicationResponse>>> tasks = List.of(3L, 4L).stream()
                    .map(userId -> (Callable<ResponseEntity<ApplicationResponse>>) () -> restTemplate.exchange(
                            url("/api/applications"), HttpMethod.POST,
                            new HttpEntity<>(new ApplicationCreateRequest(event.getId(), null, null), authHeaders(userId)),
                            ApplicationResponse.class))
                    .collect(Collectors.toList());

            List<Future<ResponseEntity<ApplicationResponse>>> futures = pool.invokeAll(tasks);
            List<String> statuses = futures.stream().map(f -> {
                try {
                    return f.get().getBody().status();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).toList();

            assertThat(statuses).containsExactlyInAnyOrder(ApplicationStatus.ACCEPTED, ApplicationStatus.WAITLISTED);
            assertThat(applicationRepository.countByEvent_IdAndStatus(event.getId(), ApplicationStatus.ACCEPTED))
                    .isEqualTo(1);
        } finally {
            pool.shutdown();
        }
    }

    // AP-23: whoamiのレスポンスに、role="admin"由来のadminフィールド（true）が含まれる（D-09）
    @Test
    void ap23_whoamiはadminフィールドを含む() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/whoami"), HttpMethod.GET, new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"admin\":true");
    }

    // AP-25: 既存の管理者は新たな管理者アカウントを登録でき、実際にDBへ管理者として保存される（D-04）
    @Test
    void ap25_管理者は管理者アカウントを登録できる() {
        UserRegisterRequest request = new UserRegisterRequest("新管理者", "new-admin@example.com");

        ResponseEntity<UserResponse> response = restTemplate.exchange(
                url("/api/admins"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(ADMIN_USER_ID)),
                UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().role()).isEqualTo("admin");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE email = ?", String.class, "new-admin@example.com"))
                .isEqualTo("admin");
    }

    // AP-25の権限チェック: 一般ユーザーは管理者アカウントを登録できない
    @Test
    void ap25_一般ユーザーは管理者アカウントを登録できない() {
        UserRegisterRequest request = new UserRegisterRequest("新管理者", "new-admin2@example.com");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/admins"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(GENERAL_USER_ID)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?",
                Integer.class, "new-admin2@example.com")).isZero();
    }

    // AP-09 format=csv（D-06）: 削除済みイベントに紐づく申込明細はCSVに含まれない
    @Test
    void ap09_csv出力は削除済みイベントの申込を含まない() {
        Event deletedEvent = openEvent();
        ApplicationCreateRequest applyRequest = new ApplicationCreateRequest(deletedEvent.getId(), null, null);
        restTemplate.exchange(url("/api/applications"), HttpMethod.POST,
                new HttpEntity<>(applyRequest, authHeaders(GENERAL_USER_ID)), ApplicationResponse.class);
        deletedEvent.softDelete();
        eventRepository.save(deletedEvent);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/reports/applications?format=csv"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).doesNotContain(deletedEvent.getName());
    }
}
