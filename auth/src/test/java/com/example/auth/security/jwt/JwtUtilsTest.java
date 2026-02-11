package com.example.auth.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

import com.example.auth.security.JwtProperties;
import com.example.auth.security.JwtUtils;

/**
 * JwtUtilsのユニットテスト。
 *
 * 【テスト設計方針】
 * - SpringのDIコンテナを使わず、JwtPropertiesを手動で作成してJwtUtilsを直接インスタンス化
 * - これにより起動が高速で、純粋なロジックのみをテストできる
 * - @SpringBootTest を使わないため、テストの実行が数ミリ秒で完了する
 *
 * @Nested: テストをグループ化するJUnit 5のアノテーション。
 *          関連するテストをまとめて可読性を向上させる。
 *
 * @DisplayName: テスト名を日本語で表示するためのアノテーション。
 *               テスト結果レポートが読みやすくなる。
 *
 * @BeforeEach: 各テストメソッドの前に実行されるセットアップメソッド。
 *              テスト間の独立性を保証する (各テストが同じ初期状態から開始)。
 */
class JwtUtilsTest {

  /**
   * テスト用のBase64エンコード済み秘密鍵。
   * HS256アルゴリズムには最低256ビット(32バイト)の鍵が必要。
   * この文字列はBase64デコードすると32バイト以上になる。
   */
  private static final String TEST_SECRET = "dGhpc0lzQVRlc3RTZWNyZXRLZXlGb3JKV1RUZXN0aW5nT25seURvTm90VXNlSW5Qcm9kMTIz";

  private JwtUtils jwtUtils;

  /**
   * 各テストの前にJwtUtilsを初期化する。
   * JwtPropertiesを手動で作成し、テスト用の設定値をセットする。
   */
  @BeforeEach
  void setUp() {
    JwtProperties props = new JwtProperties();
    props.setSecret(TEST_SECRET);
    props.setAccessTokenExpiration(900_000L); // 15分
    props.setRefreshTokenExpiration(604_800_000L); // 7日
    props.setRefreshCookieName("REFRESH_TOKEN");
    props.setCookiePath("/api");

    jwtUtils = new JwtUtils(props);
  }

  // ========================================================================
  // アクセストークン生成テスト
  // ========================================================================

  @Nested
  @DisplayName("generateAccessToken - アクセストークン生成")
  class GenerateAccessTokenTest {

    @Test
    @DisplayName("正常系: 有効なJWT文字列が生成される")
    void shouldGenerateValidJwtString() {
      // Arrange (準備): テスト対象のユーザーID
      Long userId = 42L;

      // Act (実行): トークン生成
      String token = jwtUtils.generateAccessToken(userId);

      // Assert (検証):
      // JWT形式は "header.payload.signature" の3パートをドットで区切った文字列
      assertThat(token).isNotBlank();
      assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("正常系: 生成したトークンからユーザーIDを正しく取得できる")
    void shouldExtractUserIdFromGeneratedToken() {
      Long userId = 42L;

      // トークン生成 → 検証のラウンドトリップテスト
      String token = jwtUtils.generateAccessToken(userId);
      Optional<Long> extractedId = jwtUtils.getUserIdFromAccessToken(token);

      // 生成時に設定したユーザーIDが検証時に正しく取り出せることを確認
      assertThat(extractedId).isPresent().contains(userId);
    }

    @Test
    @DisplayName("正常系: 異なるユーザーIDで異なるトークンが生成される")
    void shouldGenerateDifferentTokensForDifferentUsers() {
      String token1 = jwtUtils.generateAccessToken(1L);
      String token2 = jwtUtils.generateAccessToken(2L);

      // 同じ秘密鍵でも、ペイロード(ユーザーID)が異なるため別のトークンになる
      assertThat(token1).isNotEqualTo(token2);
    }
  }

  // ========================================================================
  // アクセストークン検証テスト
  // ========================================================================

  @Nested
  @DisplayName("getUserIdFromAccessToken - アクセストークン検証")
  class GetUserIdFromAccessTokenTest {

    @Test
    @DisplayName("異常系: 期限切れのトークンはOptional.empty()を返す")
    void shouldReturnEmptyForExpiredToken() {
      // 有効期限を0ミリ秒に設定した別のJwtUtilsを作成
      // → 生成した瞬間に期限切れになるトークンを作れる
      JwtProperties expiredProps = new JwtProperties();
      expiredProps.setSecret(TEST_SECRET);
      expiredProps.setAccessTokenExpiration(0L); // 即座に期限切れ
      expiredProps.setRefreshTokenExpiration(604_800_000L);
      expiredProps.setRefreshCookieName("REFRESH_TOKEN");
      expiredProps.setCookiePath("/api");

      JwtUtils expiredJwtUtils = new JwtUtils(expiredProps);
      String expiredToken = expiredJwtUtils.generateAccessToken(1L);

      // 期限切れトークンの検証結果はempty
      Optional<Long> result = jwtUtils.getUserIdFromAccessToken(expiredToken);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("異常系: 改ざんされたトークンはOptional.empty()を返す")
    void shouldReturnEmptyForTamperedToken() {
      String validToken = jwtUtils.generateAccessToken(1L);

      // トークンの末尾を変更して改ざんをシミュレート
      // 署名部分が変わるため、検証に失敗する
      String tamperedToken = validToken.substring(0, validToken.length() - 4) + "XXXX";

      Optional<Long> result = jwtUtils.getUserIdFromAccessToken(tamperedToken);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("異常系: 別の秘密鍵で生成されたトークンはOptional.empty()を返す")
    void shouldReturnEmptyForTokenWithDifferentKey() {
      // 別の秘密鍵で生成されたトークン → 署名検証が失敗する
      JwtProperties otherProps = new JwtProperties();
      otherProps.setSecret(
          "YW5vdGhlclNlY3JldEtleUZvckpXVFRlc3RpbmdPbmx5RG9Ob3RVc2VJblByb2QxMjM0NQ==");
      otherProps.setAccessTokenExpiration(900_000L);
      otherProps.setRefreshTokenExpiration(604_800_000L);
      otherProps.setRefreshCookieName("REFRESH_TOKEN");
      otherProps.setCookiePath("/api");

      JwtUtils otherJwtUtils = new JwtUtils(otherProps);
      String foreignToken = otherJwtUtils.generateAccessToken(1L);

      // 異なる鍵で署名されたトークンは検証に失敗する
      Optional<Long> result = jwtUtils.getUserIdFromAccessToken(foreignToken);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("異常系: 不正な文字列はOptional.empty()を返す")
    void shouldReturnEmptyForInvalidString() {
      Optional<Long> result = jwtUtils.getUserIdFromAccessToken("not.a.jwt");
      assertThat(result).isEmpty();
    }
  }

  // ========================================================================
  // Authorizationヘッダー抽出テスト
  // ========================================================================

  @Nested
  @DisplayName("getAccessTokenFromHeader - Authorizationヘッダー抽出")
  class GetAccessTokenFromHeaderTest {

    @Test
    @DisplayName("正常系: Bearer形式のヘッダーからトークンを抽出できる")
    void shouldExtractTokenFromBearerHeader() {
      // MockHttpServletRequest: Spring Testが提供するHTTPリクエストのモック
      // 実際のServletコンテナなしでリクエストをシミュレートできる
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer my-jwt-token");

      Optional<String> result = jwtUtils.getAccessTokenFromHeader(request);

      // "Bearer " プレフィックスが除去されてトークン部分のみ返る
      assertThat(result).isPresent().contains("my-jwt-token");
    }

    @Test
    @DisplayName("異常系: Authorizationヘッダーがない場合はOptional.empty()")
    void shouldReturnEmptyWhenNoAuthorizationHeader() {
      MockHttpServletRequest request = new MockHttpServletRequest();
      // ヘッダーを設定しない

      Optional<String> result = jwtUtils.getAccessTokenFromHeader(request);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("異常系: Bearer以外の形式はOptional.empty()")
    void shouldReturnEmptyForNonBearerScheme() {
      MockHttpServletRequest request = new MockHttpServletRequest();
      // Basic認証など、Bearer以外のスキーム
      request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

      Optional<String> result = jwtUtils.getAccessTokenFromHeader(request);
      assertThat(result).isEmpty();
    }
  }

  // ========================================================================
  // リフレッシュトークン生成テスト
  // ========================================================================

  @Nested
  @DisplayName("generateRefreshToken - リフレッシュトークン生成")
  class GenerateRefreshTokenTest {

    @Test
    @DisplayName("正常系: UUID形式のトークンが生成される")
    void shouldGenerateUuidFormatToken() {
      String token = jwtUtils.generateRefreshToken();

      // UUID形式の検証: "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
      // UUID.fromString() は不正な形式の場合にIllegalArgumentExceptionをスローする
      assertThat(token).isNotBlank();
      assertThat(UUID.fromString(token)).isNotNull();
    }

    @Test
    @DisplayName("正常系: 毎回異なるトークンが生成される")
    void shouldGenerateUniqueTokens() {
      String token1 = jwtUtils.generateRefreshToken();
      String token2 = jwtUtils.generateRefreshToken();

      // UUID v4はランダム生成なので、衝突はほぼあり得ない
      assertThat(token1).isNotEqualTo(token2);
    }
  }

  // ========================================================================
  // リフレッシュトークンCookieテスト
  // ========================================================================

  @Nested
  @DisplayName("generateRefreshTokenCookie - Cookie生成")
  class GenerateRefreshTokenCookieTest {

    @Test
    @DisplayName("正常系: HttpOnly, SameSite=Strict のセキュアなCookieが生成される")
    void shouldGenerateSecureCookie() {
      String token = "test-refresh-token";
      ResponseCookie cookie = jwtUtils.generateRefreshTokenCookie(token);

      // Cookie名とトークン値の検証
      assertThat(cookie.getName()).isEqualTo("REFRESH_TOKEN");
      assertThat(cookie.getValue()).isEqualTo(token);
      // セキュリティ属性の検証
      assertThat(cookie.isHttpOnly()).isTrue(); // XSS対策
      assertThat(cookie.getSameSite()).isEqualTo("Strict"); // CSRF対策
      assertThat(cookie.getPath()).isEqualTo("/api"); // 適用パス
      // 有効期限の検証 (604800000ms ÷ 1000 = 604800秒 = 7日)
      assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(604800L);
    }
  }

  @Nested
  @DisplayName("getCleanRefreshTokenCookie - Cookie無効化")
  class GetCleanRefreshTokenCookieTest {

    @Test
    @DisplayName("正常系: maxAge=0の空Cookieが生成される (ブラウザに削除させる)")
    void shouldGenerateCleanCookieWithZeroMaxAge() {
      ResponseCookie cookie = jwtUtils.getCleanRefreshTokenCookie();

      assertThat(cookie.getName()).isEqualTo("REFRESH_TOKEN");
      assertThat(cookie.getValue()).isEmpty();
      // maxAge=0: ブラウザにこのCookieを即座に削除させる
      assertThat(cookie.getMaxAge().getSeconds()).isZero();
    }
  }

  // ========================================================================
  // CookieからリフレッシュトークンMR取テスト
  // ========================================================================

  @Nested
  @DisplayName("getRefreshTokenFromCookie - Cookie抽出")
  class GetRefreshTokenFromCookieTest {

    @Test
    @DisplayName("正常系: Cookieからリフレッシュトークンを取得できる")
    void shouldExtractRefreshTokenFromCookie() {
      MockHttpServletRequest request = new MockHttpServletRequest();
      // MockHttpServletRequest.addCookie(): リクエストにCookieを追加
      request.setCookies(new jakarta.servlet.http.Cookie("REFRESH_TOKEN", "my-refresh-token"));

      Optional<String> result = jwtUtils.getRefreshTokenFromCookie(request);
      assertThat(result).isPresent().contains("my-refresh-token");
    }

    @Test
    @DisplayName("異常系: Cookieが存在しない場合はOptional.empty()")
    void shouldReturnEmptyWhenNoCookie() {
      MockHttpServletRequest request = new MockHttpServletRequest();

      Optional<String> result = jwtUtils.getRefreshTokenFromCookie(request);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("異常系: Cookie値が空文字の場合はOptional.empty()")
    void shouldReturnEmptyForBlankCookieValue() {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.setCookies(new jakarta.servlet.http.Cookie("REFRESH_TOKEN", ""));

      Optional<String> result = jwtUtils.getRefreshTokenFromCookie(request);
      assertThat(result).isEmpty();
    }
  }
}
