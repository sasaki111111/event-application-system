package com.example.eventapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.eventapp.dto.ApplicationCreateRequest;
import com.example.eventapp.dto.ApplicationResponse;
import com.example.eventapp.dto.EventDetailResponse;
import com.example.eventapp.dto.EventSummaryResponse;
import com.example.eventapp.dto.EventUpsertRequest;
import com.example.eventapp.dto.MyApplicationResponse;
import com.example.eventapp.entity.Application;
import com.example.eventapp.entity.Event;
import com.example.eventapp.repository.ApplicationRepository;
import com.example.eventapp.repository.EventRepository;
import java.time.LocalDateTime;
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
        return eventRepository.save(new Event(
                "結合テスト用イベント",
                LocalDateTime.now().plusDays(10),
                "会議室",
                5,
                LocalDateTime.now().plusDays(5),
                "G-2結合テスト用データ",
                null, null, null, null));
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
                LocalDateTime.now().plusDays(15), null, null, null, null, null, null);

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
                LocalDateTime.now().plusDays(15), null, null, null, null, null, null);

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
        ApplicationCreateRequest request = new ApplicationCreateRequest(event.getId());

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
        ApplicationCreateRequest applyRequest = new ApplicationCreateRequest(event.getId());
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
                new HttpEntity<>(new ApplicationCreateRequest(event.getId()), authHeaders(GENERAL_USER_ID)),
                ApplicationResponse.class);
        Long applicationId = applyResponse.getBody().id();

        ResponseEntity<Void> cancelResponse = restTemplate.exchange(
                url("/api/applications/" + applicationId), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), Void.class);

        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        Application cancelled = applicationRepository.findById(applicationId).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo("キャンセル済");
    }
}
