package com.example.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.auth.repository.UserRepository;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * AuthControllerの結合テスト。
 * 実際のHTTPリクエスト→Controller→Service→Repository→DBの全フローをテストする。
 *
 * 【テスト基盤】
 * - DB: Testcontainersが自動起動するPostgreSQLコンテナ (本番と同じDBエンジン)
 * - Redis: Testcontainersが自動起動するRedisコンテナ
 * - HTTP: MockMvcがServletコンテナなしでHTTP通信をシミュレート
 *
 * 【Testcontainersを使う理由】
 * - 本番と同じDBエンジン(PostgreSQL)でテストするため、SQL方言の差異による不具合を防ぐ
 * - 将来PostgreSQL固有機能(JSONB, Full-text Search等)を使った場合もテストが壊れない
 * - コンテナはテスト終了後に自動破棄されるため、環境を汚さない
 * - Docker Desktopがあれば追加設定不要で動作する
 *
 * @SpringBootTest: Springアプリケーション全体を起動する結合テスト用アノテーション。
 *   全てのBean(Controller, Service, Repository等)がDIコンテナに登録される。
 *   webEnvironment=MOCK(デフォルト): 実際のHTTPサーバーは起動せず、MockMvcで通信する。
 *
 * @AutoConfigureMockMvc: MockMvcを自動設定する。
 *   MockMvcはServletコンテナなしでHTTPリクエスト/レスポンスをシミュレートする。
 *   実際のHTTPサーバーを起動するより高速。
 *
 * @ActiveProfiles("test"): application-test.properties を読み込む。
 *   プロファイル機能により、テスト用の設定(テスト用JWT秘密鍵等)が適用される。
 *
 * @Testcontainers: JUnit 5のTestcontainers拡張を有効化する。
 *   @Container アノテーション付きのコンテナを自動的にライフサイクル管理する。
 *   テストクラス開始時にコンテナを起動、終了時に破棄。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthControllerIntegrationTest {

  /**
   * テスト用PostgreSQLコンテナの定義。
   *
   * PostgreSQLContainer: Testcontainersが提供するPostgreSQL専用コンテナクラス。
   *   GenericContainerと異なり、PostgreSQL固有の設定メソッドを提供する:
   *   - withDatabaseName(): DB名を指定
   *   - withUsername() / withPassword(): 認証情報を指定
   *   - getJdbcUrl(): JDBC接続URLを取得
   *
   * @Container + static: テストクラス全体で1つのコンテナを共有する。
   *   各テストメソッドで新しいコンテナを起動すると遅くなるため、
   *   staticフィールドにして全テストで共有する。
   *   データのクリーンアップは @BeforeEach の deleteAll() で行う。
   *
   * "postgres:16-alpine": PostgreSQL 16のAlpine Linux版 (軽量イメージ)。
   *   本番のPostgreSQLバージョンに合わせることで、互換性を保証する。
   */
  @Container
  static PostgreSQLContainer<?> postgresContainer =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("testdb")
          .withUsername("test")
          .withPassword("test");

  /**
   * テスト用Redisコンテナの定義。
   *
   * GenericContainer: 任意のDockerイメージからコンテナを作成する汎用クラス。
   *   Redis専用モジュールもあるが、GenericContainerで十分。
   *
   * withExposedPorts(6379): コンテナの6379番ポートをホストのランダムポートにマッピング。
   *   ランダムポートを使うことで、他のプロセスとのポート競合を避ける。
   *   getMappedPort(6379) で実際にマッピングされたホスト側ポートを取得できる。
   *
   * "redis:7-alpine": Redisバージョン7のAlpine Linux版 (軽量イメージ、約30MB)。
   */
  @Container
  static GenericContainer<?> redisContainer =
      new GenericContainer<>("redis:7-alpine")
          .withExposedPorts(6379);

  /**
   * Testcontainersの動的プロパティ設定。
   *
   * @DynamicPropertySource: テスト実行時にSpringのプロパティを動的に上書きする。
   *   Testcontainersのコンテナはランダムなポートにマッピングされるため、
   *   application.propertiesに固定値を書くことができない。
   *   このメソッドでコンテナの実際のホスト・ポートを動的に設定する。
   *
   * 【なぜ動的設定が必要か】
   *   通常の application.properties では "localhost:5432" のように固定値を書くが、
   *   Testcontainersはポート競合を避けるためにランダムポートを割り当てる。
   *   例: PostgreSQL 5432 → 32789, Redis 6379 → 32790
   *   このランダムポートをSpringの設定に反映させるのが @DynamicPropertySource の役割。
   *
   * @param registry プロパティ登録用オブジェクト。ラムダで値を遅延評価する。
   */
  @DynamicPropertySource
  static void containerProperties(DynamicPropertyRegistry registry) {
    // ===== PostgreSQL =====
    // PostgreSQLContainerが提供するJDBC URLをそのまま使用
    // 例: "jdbc:postgresql://localhost:32789/testdb"
    registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
    registry.add("spring.datasource.username", postgresContainer::getUsername);
    registry.add("spring.datasource.password", postgresContainer::getPassword);

    // ===== Redis =====
    // コンテナのホストアドレス (通常は "localhost")
    registry.add("spring.data.redis.host", redisContainer::getHost);
    // コンテナのポート (6379 → ランダムなホストポートにマッピングされている)
    registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
  }

  /**
   * MockMvc: HTTPリクエストをシミュレートするSpring Testのユーティリティ。
   * perform(): リクエストを実行
   * andExpect(): レスポンスの検証
   * andReturn(): レスポンスの取得
   *
   * @Autowired: SpringのDIコンテナからBeanを自動注入する。
   *   @AutoConfigureMockMvcにより、MockMvcがBeanとして登録されている。
   */
  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private ObjectMapper objectMapper;

  /**
   * 各テストの前にDBをクリーンアップする。
   * テスト間の独立性を保証するため、前のテストで作成されたデータを削除する。
   *
   * @BeforeEach: 各テストメソッドの実行前に呼ばれるセットアップメソッド。
   *   @BeforeAll(クラスで1回)と異なり、各テストで個別に実行される。
   */
  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
  }

  // ========================================================================
  // サインアップテスト
  // ========================================================================

  @Nested
  @DisplayName("POST /api/users/signup - ユーザー登録")
  class SignupTest {

    @Test
    @DisplayName("正常系: 新規ユーザーが登録され、アクセストークン(JSON)+リフレッシュトークン(Cookie)が返る")
    void shouldSignupSuccessfully() throws Exception {
      // Arrange: リクエストボディのJSON
      String requestBody = """
          {
            "email": "test@example.com",
            "password": "password123"
          }
          """;

      // Act & Assert:
      // mockMvc.perform(): HTTPリクエストをシミュレート
      // post(): POSTメソッドを指定
      // contentType(): Content-Typeヘッダーを設定
      // content(): リクエストボディを設定
      MvcResult result = mockMvc.perform(post("/api/users/signup")
              .contentType(MediaType.APPLICATION_JSON)
              .content(requestBody))
          // andExpect(): レスポンスの検証
          // status().isCreated(): HTTPステータスが201 Createdであることを確認
          .andExpect(status().isCreated())
          // jsonPath(): レスポンスJSONの特定フィールドを検証
          // $.accessToken: ルートオブジェクトのaccessTokenフィールド
          // isNotEmpty(): 値が空でないことを確認
          .andExpect(jsonPath("$.accessToken").isNotEmpty())
          .andExpect(jsonPath("$.message").value("signup success"))
          // andReturn(): MvcResult(レスポンス全体)を取得
          .andReturn();

      // Set-Cookieヘッダーの検証
      // リフレッシュトークンがHttpOnly Cookieとして返されていることを確認
      String setCookieHeader = result.getResponse().getHeader("Set-Cookie");
      assertThat(setCookieHeader)
          .contains("REFRESH_TOKEN")  // Cookie名
          .contains("HttpOnly")       // XSS対策
          .contains("SameSite=Strict"); // CSRF対策

      // DBにユーザーが保存されたことを確認
      assertThat(userRepository.existsByEmail("test@example.com")).isTrue();
    }

    @Test
    @DisplayName("異常系: 既存メールアドレスで登録しようとすると400 Bad Request")
    void shouldReturn400WhenEmailAlreadyExists() throws Exception {
      // 1回目: 正常に登録
      String requestBody = """
          {
            "email": "duplicate@example.com",
            "password": "password123"
          }
          """;
      mockMvc.perform(post("/api/users/signup")
              .contentType(MediaType.APPLICATION_JSON)
              .content(requestBody))
          .andExpect(status().isCreated());

      // 2回目: 同じメールアドレスで再登録 → 400 Bad Request
      mockMvc.perform(post("/api/users/signup")
              .contentType(MediaType.APPLICATION_JSON)
              .content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("ユーザーが既に存在します"));
    }

    @Test
    @DisplayName("異常系: バリデーションエラー (メールアドレスが空)")
    void shouldReturn400WhenEmailIsBlank() throws Exception {
      String requestBody = """
          {
            "email": "",
            "password": "password123"
          }
          """;

      // @Valid + @NotBlank でバリデーションエラー → 400 Bad Request
      mockMvc.perform(post("/api/users/signup")
              .contentType(MediaType.APPLICATION_JSON)
              .content(requestBody))
          .andExpect(status().isBadRequest());
    }
  }

  // ========================================================================
  // サインインテスト
  // ========================================================================

  @Nested
  @DisplayName("POST /api/users/signin - ログイン")
  class SigninTest {

    /**
     * @BeforeEach はネストされた@Nestedクラス内でも使用可能。
     * 外側の@BeforeEach (deleteAll) が先に実行され、
     * その後この内側の@BeforeEach (ユーザー作成) が実行される。
     */
    @BeforeEach
    void createTestUser() throws Exception {
      // テスト前にユーザーを登録しておく
      String requestBody = """
          {
            "email": "user@example.com",
            "password": "password123"
          }
          """;
      mockMvc.perform(post("/api/users/signup")
              .contentType(MediaType.APPLICATION_JSON)
              .content(requestBody))
          .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("正常系: 正しい認証情報でログインできる")
    void shouldSigninSuccessfully() throws Exception {
      String requestBody = """
          {
            "email": "user@example.com",
            "password": "password123"
          }
          """;

      MvcResult result = mockMvc.perform(post("/api/users/signin")
              .contentType(MediaType.APPLICATION_JSON)
              .content(requestBody))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.accessToken").isNotEmpty())
          .andExpect(jsonPath("$.message").value("login success"))
          .andReturn();

      // リフレッシュトークンCookieが設定されていることを確認
      String setCookieHeader = result.getResponse().getHeader("Set-Cookie");
      assertThat(setCookieHeader).contains("REFRESH_TOKEN");
    }

    @Test
    @DisplayName("異常系: 誤ったパスワードでログインすると401 Unauthorized")
    void shouldReturn401WithWrongPassword() throws Exception {
      String requestBody = """
          {
            "email": "user@example.com",
            "password": "wrong-password"
          }
          """;

      // AuthenticationManagerが認証失敗 → AuthEntryPointJwtが401を返す
      mockMvc.perform(post("/api/users/signin")
              .contentType(MediaType.APPLICATION_JSON)
              .content(requestBody))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("異常系: 存在しないメールアドレスでログインすると401 Unauthorized")
    void shouldReturn401WithNonexistentEmail() throws Exception {
      String requestBody = """
          {
            "email": "nobody@example.com",
            "password": "password123"
          }
          """;

      mockMvc.perform(post("/api/users/signin")
              .contentType(MediaType.APPLICATION_JSON)
              .content(requestBody))
          .andExpect(status().isUnauthorized());
    }
  }

  // ========================================================================
  // サインアウトテスト
  // ========================================================================

  @Nested
  @DisplayName("POST /api/users/signout - ログアウト")
  class SignoutTest {

    @Test
    @DisplayName("正常系: ログアウトするとCookieが無効化される")
    void shouldSignoutAndClearCookie() throws Exception {
      // 1. ユーザー登録してリフレッシュトークンCookieを取得
      String requestBody = """
          {
            "email": "signout@example.com",
            "password": "password123"
          }
          """;
      MvcResult signupResult = mockMvc.perform(post("/api/users/signup")
              .contentType(MediaType.APPLICATION_JSON)
              .content(requestBody))
          .andExpect(status().isCreated())
          .andReturn();

      // Set-CookieヘッダーからCookieを解析
      Cookie refreshCookie = signupResult.getResponse().getCookie("REFRESH_TOKEN");

      // 2. リフレッシュトークンCookieを付けてログアウト
      // cookie(): リクエストにCookieを付与 (ブラウザが自動的に行う動作をシミュレート)
      MvcResult signoutResult = mockMvc.perform(post("/api/users/signout")
              .cookie(refreshCookie))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("logout success"))
          .andReturn();

      // 3. レスポンスのSet-CookieでCookieが無効化されていることを確認
      String setCookieHeader = signoutResult.getResponse().getHeader("Set-Cookie");
      // Max-Age=0: ブラウザにCookieを即座に削除させる
      assertThat(setCookieHeader).contains("Max-Age=0");
    }
  }

  // ========================================================================
  // トークンリフレッシュテスト
  // ========================================================================

  @Nested
  @DisplayName("POST /api/users/refresh - トークンリフレッシュ")
  class RefreshTest {

    @Test
    @DisplayName("正常系: 有効なリフレッシュトークンで新しいアクセストークンを取得できる")
    void shouldRefreshTokenSuccessfully() throws Exception {
      // 1. ユーザー登録
      String requestBody = """
          {
            "email": "refresh@example.com",
            "password": "password123"
          }
          """;
      MvcResult signupResult = mockMvc.perform(post("/api/users/signup")
              .contentType(MediaType.APPLICATION_JSON)
              .content(requestBody))
          .andExpect(status().isCreated())
          .andReturn();

      // サインアップ時のアクセストークンを取得
      String signupResponseBody = signupResult.getResponse().getContentAsString();
      JsonNode signupJson = objectMapper.readTree(signupResponseBody);
      String originalAccessToken = signupJson.get("accessToken").asText();

      // サインアップ時のリフレッシュトークンCookieを取得
      Cookie refreshCookie = signupResult.getResponse().getCookie("REFRESH_TOKEN");

      // 2. リフレッシュエンドポイントを呼び出し
      MvcResult refreshResult = mockMvc.perform(post("/api/users/refresh")
              .cookie(refreshCookie))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.accessToken").isNotEmpty())
          .andExpect(jsonPath("$.message").value("token refreshed"))
          .andReturn();

      // 3. 新しいアクセストークンが元のものと異なることを確認
      String refreshResponseBody = refreshResult.getResponse().getContentAsString();
      JsonNode refreshJson = objectMapper.readTree(refreshResponseBody);
      String newAccessToken = refreshJson.get("accessToken").asText();
      assertThat(newAccessToken).isNotEqualTo(originalAccessToken);

      // 4. 新しいリフレッシュトークンCookieが設定されていることを確認 (トークンローテーション)
      Cookie newRefreshCookie = refreshResult.getResponse().getCookie("REFRESH_TOKEN");
      assertThat(newRefreshCookie).isNotNull();
      // 新しいリフレッシュトークンは古いものと異なる (ローテーション)
      assertThat(newRefreshCookie.getValue()).isNotEqualTo(refreshCookie.getValue());
    }

    @Test
    @DisplayName("正常系: ローテーション後の古いトークンは使用不可になる")
    void shouldRejectOldTokenAfterRotation() throws Exception {
      // 1. ユーザー登録
      String requestBody = """
          {
            "email": "rotate@example.com",
            "password": "password123"
          }
          """;
      MvcResult signupResult = mockMvc.perform(post("/api/users/signup")
              .contentType(MediaType.APPLICATION_JSON)
              .content(requestBody))
          .andExpect(status().isCreated())
          .andReturn();

      Cookie originalCookie = signupResult.getResponse().getCookie("REFRESH_TOKEN");

      // 2. 1回目のリフレッシュ (成功) → 古いトークンは削除される
      mockMvc.perform(post("/api/users/refresh")
              .cookie(originalCookie))
          .andExpect(status().isOk());

      // 3. 同じ(古い)トークンで2回目のリフレッシュ → 401 Unauthorized
      // トークンローテーションにより、古いトークンはRedisから削除済み
      mockMvc.perform(post("/api/users/refresh")
              .cookie(originalCookie))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.message").value("無効なリフレッシュトークンです"));
    }

    @Test
    @DisplayName("異常系: Cookieなしでリフレッシュすると401 Unauthorized")
    void shouldReturn401WhenNoCookie() throws Exception {
      // リフレッシュトークンCookieを付けずにリクエスト
      mockMvc.perform(post("/api/users/refresh"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.message").value("リフレッシュトークンがありません"));
    }

    @Test
    @DisplayName("異常系: 不正なリフレッシュトークンで401 Unauthorized")
    void shouldReturn401WithInvalidToken() throws Exception {
      // 存在しないトークン値のCookieを送信
      Cookie fakeCookie = new Cookie("REFRESH_TOKEN", "non-existent-token");

      mockMvc.perform(post("/api/users/refresh")
              .cookie(fakeCookie))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.message").value("無効なリフレッシュトークンです"));
    }

    @Test
    @DisplayName("正常系: ログアウト後のリフレッシュトークンは使用不可")
    void shouldRejectTokenAfterSignout() throws Exception {
      // 1. ユーザー登録
      String requestBody = """
          {
            "email": "logout-refresh@example.com",
            "password": "password123"
          }
          """;
      MvcResult signupResult = mockMvc.perform(post("/api/users/signup")
              .contentType(MediaType.APPLICATION_JSON)
              .content(requestBody))
          .andExpect(status().isCreated())
          .andReturn();

      Cookie refreshCookie = signupResult.getResponse().getCookie("REFRESH_TOKEN");

      // 2. ログアウト (Redisからトークン削除)
      mockMvc.perform(post("/api/users/signout")
              .cookie(refreshCookie))
          .andExpect(status().isOk());

      // 3. ログアウト後に同じトークンでリフレッシュ → 401 Unauthorized
      // Redisから削除されているため、検証に失敗する
      mockMvc.perform(post("/api/users/refresh")
              .cookie(refreshCookie))
          .andExpect(status().isUnauthorized());
    }
  }

  // ========================================================================
  // 認証付きリクエストテスト
  // ========================================================================

  @Nested
  @DisplayName("認証付きリクエスト - Authorizationヘッダー")
  class AuthenticatedRequestTest {

    @Test
    @DisplayName("正常系: アクセストークンをAuthorizationヘッダーに付与してAPIにアクセスできる")
    void shouldAccessProtectedEndpointWithBearerToken() throws Exception {
      // 1. ユーザー登録してアクセストークンを取得
      String requestBody = """
          {
            "email": "protected@example.com",
            "password": "password123"
          }
          """;
      MvcResult signupResult = mockMvc.perform(post("/api/users/signup")
              .contentType(MediaType.APPLICATION_JSON)
              .content(requestBody))
          .andExpect(status().isCreated())
          .andReturn();

      String responseBody = signupResult.getResponse().getContentAsString();
      JsonNode json = objectMapper.readTree(responseBody);
      String accessToken = json.get("accessToken").asText();

      // 2. アクセストークンでサインアウト (認証が必要なエンドポイントの代わりにテスト)
      // Authorizationヘッダーに "Bearer <token>" 形式でトークンを付与
      mockMvc.perform(post("/api/users/signout")
              .header("Authorization", "Bearer " + accessToken))
          .andExpect(status().isOk());
    }
  }
}
