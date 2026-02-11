package com.example.auth.service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.example.auth.security.JwtUtils;

/**
 * リフレッシュトークンのライフサイクルをRedisで管理するサービス。
 *
 * 【Redisでのデータ構造】
 * キー: "refresh_token:{トークン文字列}" (例: "refresh_token:550e8400-e29b-...")
 * 値: ユーザーID文字列 (例: "42")
 * TTL: リフレッシュトークンの有効期限 (例: 7日)
 *
 * 【このサービスの責務】
 * - トークンの保存 (ログイン時)
 * - トークンの検証とユーザーID取得 (リフレッシュ時)
 * - トークンの削除 (ログアウト時)
 * - トークンのローテーション (リフレッシュ時に旧トークンを削除し新トークンを発行)
 *
 * @Service: Springのサービス層コンポーネントとして登録。
 *           ビジネスロジックを担当するクラスに付与する。
 * @Componentと機能は同じだが、役割を明示するためのステレオタイプアノテーション。
 */
@Service
public class RefreshTokenService {

  /** Redisキーのプレフィックス。名前空間を分けて他のキーと衝突を防ぐ */
  private static final String KEY_PREFIX = "refresh_token:";

  private final RedisTemplate<String, String> redisTemplate;
  private final JwtUtils jwtUtils;

  /**
   * コンストラクタインジェクション。
   * Springが起動時にRedisTemplateとJwtUtilsを自動注入する。
   *
   * @param redisTemplate RedisConfigで定義したBean (String型キー・値用)
   * @param jwtUtils      JWT関連ユーティリティ (有効期限の取得に使用)
   */
  public RefreshTokenService(RedisTemplate<String, String> redisTemplate, JwtUtils jwtUtils) {
    this.redisTemplate = redisTemplate;
    this.jwtUtils = jwtUtils;
  }

  /**
   * リフレッシュトークンをRedisに保存する。
   *
   * 【処理フロー】
   * 1. トークン文字列からRedisキーを生成
   * 2. ユーザーIDを値として保存
   * 3. TTL(有効期限)を設定 → 期限切れ後にRedisが自動削除
   *
   * 【TTLの重要性】
   * TTLを設定しないとトークンがRedisに永久に残り、メモリリークの原因になる。
   * 期限切れトークンはRedisが自動的にeviction(追い出し)するため、
   * 別途クリーンアップバッチを作る必要がない。
   *
   * @param refreshToken リフレッシュトークン文字列 (UUID)
   * @param userId       紐付けるユーザーID
   */
  public void save(String refreshToken, Long userId) {
    // opsForValue(): Redisの文字列型操作を提供するオブジェクト
    // set(key, value, timeout, unit): 値の保存とTTL設定を同時に行う
    redisTemplate.opsForValue().set(
        KEY_PREFIX + refreshToken, // キー: "refresh_token:uuid-string"
        String.valueOf(userId), // 値: "42" (ユーザーID文字列)
        jwtUtils.getRefreshTokenExpiration(), // TTL: 604800000 (7日分のミリ秒)
        TimeUnit.MILLISECONDS // TTLの単位
    );
  }

  /**
   * リフレッシュトークンを検証し、紐付けられたユーザーIDを取得する。
   *
   * 【検証ロジック】
   * - Redisにキーが存在する → トークンは有効 → ユーザーIDを返す
   * - Redisにキーが存在しない → トークンは無効(期限切れ/ログアウト済み/不正)
   *
   * @param refreshToken 検証するリフレッシュトークン
   * @return ユーザーID (無効なトークンの場合はOptional.empty())
   */
  public Optional<Long> findUserIdByToken(String refreshToken) {
    // Redisからキーに対応する値を取得 (存在しなければnull)
    String userId = redisTemplate.opsForValue().get(KEY_PREFIX + refreshToken);

    return Optional.ofNullable(userId)
        // 文字列 → Long に変換
        .map(Long::valueOf);
  }

  /**
   * リフレッシュトークンをRedisから削除する。
   * ログアウト時やトークンローテーション時に呼び出す。
   *
   * 【削除の意味】
   * Redisからトークンを削除すると、そのトークンでのリフレッシュが不可能になる。
   * たとえCookieにトークンが残っていても、サーバー側で拒否できる。
   * これがJWTだけでは実現できない「サーバー側での即時無効化」の仕組み。
   *
   * @param refreshToken 削除するリフレッシュトークン
   */
  public void delete(String refreshToken) {
    // delete(): Redisからキーを削除 (存在しないキーの削除はエラーにならない)
    redisTemplate.delete(KEY_PREFIX + refreshToken);
  }

  /**
   * トークンローテーション: 古いトークンを削除し、新しいトークンを発行・保存する。
   *
   * 【トークンローテーションとは】
   * リフレッシュの度に新しいリフレッシュトークンを発行し、古いものを無効化する戦略。
   *
   * 【セキュリティ上のメリット】
   * - リフレッシュトークンが漏洩しても、1回使われた時点で無効化される
   * - 攻撃者がトークンを使うと正規ユーザーのトークンが無効化され、異常を検知できる
   * - トークンの有効期間が事実上「次のリフレッシュまで」に短縮される
   *
   * @param oldRefreshToken 無効化する古いトークン
   * @param userId          新しいトークンに紐付けるユーザーID
   * @return 新しいリフレッシュトークン文字列
   */
  public String rotate(String oldRefreshToken, Long userId) {
    // 1. 古いトークンをRedisから削除 (即座に無効化)
    delete(oldRefreshToken);

    // 2. 新しいリフレッシュトークンを生成
    String newRefreshToken = jwtUtils.generateRefreshToken();

    // 3. 新しいトークンをRedisに保存
    save(newRefreshToken, userId);

    return newRefreshToken;
  }
}
