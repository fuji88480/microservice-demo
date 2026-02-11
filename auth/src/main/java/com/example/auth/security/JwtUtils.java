package com.example.auth.security;

import java.security.Key;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * JWT関連のユーティリティクラス。
 * アクセストークンとリフレッシュトークンの生成・検証・抽出を担当する。
 *
 * 【トークン戦略】
 * - アクセストークン: レスポンスボディ(JSON)で返却 → フロントエンドがAuthorizationヘッダーに付与
 * 短命(15分)で、サーバー側でのステート管理不要
 * - リフレッシュトークン: HttpOnly Cookieに格納 → XSSで盗まれない
 * 長命(7日)で、Redisに保存しサーバー側で無効化可能
 *
 * @Component: Springのコンポーネントとして登録。他のクラスからDIで利用可能にする
 */
@Component
public class JwtUtils {
  private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

  /** Authorizationヘッダーの接頭辞 */
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtProperties jwtProperties;

  /**
   * JWT署名に使用するHMAC-SHA鍵。
   * Base64エンコードされた秘密鍵文字列からバイト列にデコードし、
   * HMAC-SHAアルゴリズム用のKeyオブジェクトを生成する。
   */
  private final Key key;

  /**
   * コンストラクタインジェクション。
   * Springが起動時にJwtPropertiesを自動注入し、署名鍵を初期化する。
   */
  public JwtUtils(JwtProperties jwtProperties) {
    this.jwtProperties = jwtProperties;
    // Base64文字列 → バイト配列 → HMAC-SHA用のKeyオブジェクト
    this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecret()));
  }

  // ========================================================================
  // アクセストークン関連
  // ========================================================================

  /**
   * アクセストークンを生成する。
   * JWTのペイロードにユーザーIDや発行者情報をクレームとして含める。
   *
   * @param userId トークンの所有者となるユーザーID
   * @return JWT文字列 (header.payload.signature の形式)
   *
   *         【JWTの構造】
   *         - Header: アルゴリズム(HS256)とトークンタイプ(JWT)
   *         - Payload: 以下のクレーム(主張)を含む
   *         - sub (subject): ユーザーID - このトークンの所有者
   *         - iat (issued at): 発行時刻
   *         - exp (expiration): 有効期限
   *         - iss (issuer): 発行者 "auth" - このサービスが発行した証明
   *         - aud (audience): 対象者 "api" - APIサービス向けのトークン
   *         - type: "access" - アクセストークンであることを示すカスタムクレーム
   *         - Signature: Header+PayloadをHS256で署名
   */
  public String generateAccessToken(Long userId) {
    Date now = new Date();

    return Jwts.builder()
        // sub: トークンの所有者 (ユーザーID)
        .setSubject(String.valueOf(userId))
        // iat: トークンの発行時刻
        .setIssuedAt(now)
        // exp: トークンの有効期限 (現在時刻 + 設定値)
        .setExpiration(new Date(now.getTime() + jwtProperties.getAccessTokenExpiration()))
        // iss: トークンの発行者 (authサービス)
        .setIssuer("auth")
        // aud: トークンの対象者 (APIサービス)
        .setAudience("api")
        // カスタムクレーム: トークン種別を"access"として識別
        .claim("type", "access")
        // HS256アルゴリズムで署名 (改ざん防止)
        .signWith(key, SignatureAlgorithm.HS256)
        // 上記すべてをエンコードしてJWT文字列を生成
        .compact();
  }

  /**
   * HTTPリクエストのAuthorizationヘッダーからアクセストークンを抽出する。
   *
   * Authorizationヘッダーの形式: "Bearer <JWT文字列>"
   * "Bearer "プレフィックスを除去してJWT文字列のみを返す。
   *
   * @param request HTTPリクエスト
   * @return アクセストークン文字列 (存在しない/不正な場合はOptional.empty())
   */
  public Optional<String> getAccessTokenFromHeader(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");

    // ヘッダーが存在し、"Bearer "で始まるか検証
    if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
      // "Bearer "の後のJWT文字列部分を抽出
      return Optional.of(authHeader.substring(BEARER_PREFIX.length()));
    }

    return Optional.empty();
  }

  /**
   * アクセストークンを検証し、ペイロードからユーザーIDを取得する。
   *
   * 検証内容:
   * 1. 署名の検証 (改ざんされていないか)
   * 2. 有効期限の検証 (期限切れでないか)
   * 3. issuer(発行者)が"auth"であるか
   * 4. audience(対象者)が"api"であるか
   * 5. typeクレームが"access"であるか (リフレッシュトークンの誤用防止)
   *
   * @param token JWT文字列
   * @return ユーザーID (検証失敗時はOptional.empty())
   */
  public Optional<Long> getUserIdFromAccessToken(String token) {
    try {
      Claims claims = Jwts.parserBuilder()
          // 署名検証に使う鍵を設定
          .setSigningKey(key)
          // issuerが"auth"であることを必須とする
          .requireIssuer("auth")
          // audienceが"api"であることを必須とする
          .requireAudience("api")
          .build()
          // トークンをパースし署名を検証 (失敗時は例外がスローされる)
          .parseClaimsJws(token)
          // ペイロード(クレーム)を取得
          .getBody();

      // typeクレームが"access"であることを確認
      // リフレッシュトークンがアクセストークンとして使われるのを防ぐ
      if (!"access".equals(claims.get("type"))) {
        throw new JwtException("アクセストークンではありません");
      }

      // subjectクレーム(ユーザーID文字列)をLong型に変換して返す
      return Optional.of(Long.valueOf(claims.getSubject()));

    } catch (ExpiredJwtException e) {
      // トークンの有効期限切れ (正常なフロー - クライアントはリフレッシュを試みる)
      logger.info("アクセストークンの有効期限が切れています");
    } catch (JwtException e) {
      // その他のJWTエラー (改ざん、不正な形式、issuer不一致等)
      logger.warn("無効なアクセストークンです", e);
    }

    return Optional.empty();
  }

  // ========================================================================
  // リフレッシュトークン関連
  // ========================================================================

  /**
   * リフレッシュトークンを生成する。
   *
   * 【設計判断: JWTではなくUUIDを使う理由】
   * - リフレッシュトークンはRedisで状態管理するため、JWTの自己完結性は不要
   * - UUIDは推測不可能なランダム値で、セキュリティ上十分
   * - ペイロードに情報を含める必要がない (ユーザーIDはRedis側で紐付け)
   * - JWTより短い文字列でCookieサイズを節約できる
   *
   * @return UUID形式のリフレッシュトークン文字列
   */
  public String generateRefreshToken() {
    return UUID.randomUUID().toString();
  }

  /**
   * リフレッシュトークンをHttpOnly Cookieとして設定するためのResponseCookieを生成する。
   *
   * 【HttpOnly Cookieを使う理由】
   * - JavaScriptからアクセスできない → XSS攻撃でトークンを盗まれない
   * - ブラウザが自動的にリクエストに付与 → フロントエンドの実装が不要
   * - SameSite=Strict → CSRF攻撃を防止
   *
   * @param refreshToken リフレッシュトークン文字列
   * @return ResponseCookieオブジェクト (Set-Cookieヘッダーとして使用)
   */
  public ResponseCookie generateRefreshTokenCookie(String refreshToken) {
    return ResponseCookie
        // Cookie名とトークン値を設定
        .from(jwtProperties.getRefreshCookieName(), refreshToken)
        // Cookieの適用パス (このパス配下のリクエストでのみCookieが送信される)
        .path(jwtProperties.getCookiePath())
        // JavaScriptからのアクセスを禁止 (XSS対策の要)
        .httpOnly(true)
        // HTTPS通信でのみCookieを送信 (本番環境では必須)
        // .secure(true)
        // 同一サイトからのリクエストのみCookieを送信 (CSRF対策)
        .sameSite("Strict")
        // Cookieの有効期限 (秒単位なのでミリ秒から変換)
        .maxAge(jwtProperties.getRefreshTokenExpiration() / 1000)
        .build();
  }

  /**
   * ログアウト用: リフレッシュトークンCookieを無効化する。
   * 空文字のCookieをmaxAge=0で設定し、ブラウザにCookieを削除させる。
   *
   * @return 無効化用のResponseCookie
   */
  public ResponseCookie getCleanRefreshTokenCookie() {
    return ResponseCookie
        .from(jwtProperties.getRefreshCookieName(), "")
        .path(jwtProperties.getCookiePath())
        .httpOnly(true)
        // .secure(true)
        .sameSite("Strict")
        // maxAge=0: ブラウザにこのCookieを即座に削除させる
        .maxAge(0)
        .build();
  }

  /**
   * HTTPリクエストのCookieからリフレッシュトークンを抽出する。
   *
   * WebUtils.getCookie()はServlet APIのCookie配列から名前で検索する
   * Spring Frameworkのユーティリティメソッド。
   *
   * @param request HTTPリクエスト
   * @return リフレッシュトークン文字列 (存在しない/空の場合はOptional.empty())
   */
  public Optional<String> getRefreshTokenFromCookie(HttpServletRequest request) {
    return Optional
        // Cookie名で検索 (存在しなければnull → Optional.empty())
        .ofNullable(WebUtils.getCookie(request, jwtProperties.getRefreshCookieName()))
        // CookieオブジェクトからString値を取得
        .map(Cookie::getValue)
        // 空文字をフィルタリング (空のCookieは無効として扱う)
        .filter(StringUtils::hasText);
  }

  /**
   * リフレッシュトークンの有効期限(ミリ秒)を返す。
   * RefreshTokenServiceがRedisのTTL設定に使用する。
   */
  public long getRefreshTokenExpiration() {
    return jwtProperties.getRefreshTokenExpiration();
  }
}
