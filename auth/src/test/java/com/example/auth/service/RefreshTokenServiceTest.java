package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.example.auth.security.JwtUtils;

/**
 * RefreshTokenServiceのユニットテスト。
 *
 * 【テスト戦略】
 * RedisTemplateとJwtUtilsをMockitoでモック化し、
 * RefreshTokenServiceのビジネスロジックだけをテストする。
 *
 * 実際のRedisサーバーは不要 → 高速で安定したテスト。
 * Redisとの結合テストはAuthControllerIntegrationTestで行う。
 *
 * @ExtendWith(MockitoExtension.class): JUnit 5でMockitoを有効化する。
 * 
 * @Mock アノテーションが付いたフィールドに自動的にモックオブジェクトを注入する。
 *       Spring Boot 4.xの@MockitoBeanとは異なり、Springコンテキストを起動しない。
 *
 * @Mock: Mockitoがインターフェースや抽象クラスの偽の実装を自動生成する。
 *        モックは全メソッドがデフォルトでnull/0/falseを返す。
 *        when().thenReturn() で戻り値を指定、verify() で呼び出しを検証できる。
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  @Mock
  private RedisTemplate<String, String> redisTemplate;

  /**
   * ValueOperations: Redisの文字列型(String)操作を担当するインターフェース。
   * redisTemplate.opsForValue() の戻り値。
   * set(), get() などのメソッドを提供する。
   */
  @Mock
  private ValueOperations<String, String> valueOperations;

  @Mock
  private JwtUtils jwtUtils;

  private RefreshTokenService refreshTokenService;

  @BeforeEach
  void setUp() {
    // redisTemplate.opsForValue() が呼ばれたらモックのvalueOperationsを返す
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    // JwtUtilsのgetRefreshTokenExpiration()がテスト用の値を返すよう設定
    lenient().when(jwtUtils.getRefreshTokenExpiration()).thenReturn(604_800_000L);

    refreshTokenService = new RefreshTokenService(redisTemplate, jwtUtils);
  }

  // ========================================================================
  // save テスト
  // ========================================================================

  @Nested
  @DisplayName("save - リフレッシュトークンのRedis保存")
  class SaveTest {

    @Test
    @DisplayName("正常系: 正しいキー・値・TTLでRedisに保存される")
    void shouldSaveTokenWithCorrectKeyAndTtl() {
      String token = "test-uuid-token";
      Long userId = 42L;

      // Act: save実行
      refreshTokenService.save(token, userId);

      // Assert: RedisのopsForValue().set() が正しいパラメータで呼ばれたことを検証
      // verify(): モックのメソッドが指定した引数で呼ばれたことを確認する
      // eq(): 引数が完全一致することを検証するMockitoのマッチャー
      verify(valueOperations).set(
          eq("refresh_token:test-uuid-token"), // キー: プレフィックス + トークン
          eq("42"), // 値: ユーザーID文字列
          eq(604_800_000L), // TTL: 7日 (ミリ秒)
          eq(TimeUnit.MILLISECONDS) // TTLの単位
      );
    }
  }

  // ========================================================================
  // findUserIdByToken テスト
  // ========================================================================

  @Nested
  @DisplayName("findUserIdByToken - トークンからユーザーID取得")
  class FindUserIdByTokenTest {

    @Test
    @DisplayName("正常系: Redisに存在するトークンからユーザーIDを取得できる")
    void shouldReturnUserIdWhenTokenExists() {
      String token = "existing-token";
      // Redisのget()が"42"を返すよう設定
      when(valueOperations.get("refresh_token:existing-token")).thenReturn("42");

      Optional<Long> result = refreshTokenService.findUserIdByToken(token);

      // 文字列"42" → Long 42L に正しく変換されることを確認
      assertThat(result).isPresent().contains(42L);
    }

    @Test
    @DisplayName("異常系: Redisに存在しないトークンはOptional.empty()")
    void shouldReturnEmptyWhenTokenDoesNotExist() {
      String token = "nonexistent-token";
      // Redisのget()がnullを返す (キーが存在しない)
      when(valueOperations.get("refresh_token:nonexistent-token")).thenReturn(null);

      Optional<Long> result = refreshTokenService.findUserIdByToken(token);

      assertThat(result).isEmpty();
    }
  }

  // ========================================================================
  // delete テスト
  // ========================================================================

  @Nested
  @DisplayName("delete - リフレッシュトークンの削除")
  class DeleteTest {

    @Test
    @DisplayName("正常系: 正しいキーでRedisから削除される")
    void shouldDeleteCorrectKey() {
      String token = "token-to-delete";

      refreshTokenService.delete(token);

      // RedisTemplateのdelete()が正しいキーで呼ばれたことを検証
      verify(redisTemplate).delete("refresh_token:token-to-delete");
    }
  }

  // ========================================================================
  // rotate テスト
  // ========================================================================

  @Nested
  @DisplayName("rotate - トークンローテーション")
  class RotateTest {

    @Test
    @DisplayName("正常系: 古いトークンを削除し、新しいトークンを保存して返す")
    void shouldDeleteOldAndSaveNewToken() {
      String oldToken = "old-token";
      String newToken = "new-uuid-token";
      Long userId = 42L;

      // JwtUtils.generateRefreshToken()が新しいトークンを返すよう設定
      when(jwtUtils.generateRefreshToken()).thenReturn(newToken);

      // Act: ローテーション実行
      String result = refreshTokenService.rotate(oldToken, userId);

      // Assert 1: 古いトークンがRedisから削除されたことを確認
      verify(redisTemplate).delete("refresh_token:old-token");

      // Assert 2: 新しいトークンがRedisに保存されたことを確認
      verify(valueOperations).set(
          eq("refresh_token:new-uuid-token"),
          eq("42"),
          anyLong(),
          any(TimeUnit.class));

      // Assert 3: 新しいトークン文字列が返されることを確認
      assertThat(result).isEqualTo(newToken);
    }
  }
}
