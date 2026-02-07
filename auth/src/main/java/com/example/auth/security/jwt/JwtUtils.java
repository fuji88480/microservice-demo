package com.example.auth.security.jwt;

import java.security.Key;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;

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

@Component
public class JwtUtils {
  private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

  private final JwtProperties jwtProperties;
  // JWT署名に使用する秘密鍵
  private final Key key;

  public JwtUtils(JwtProperties jwtProperties) {
    this.jwtProperties = jwtProperties;
    this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecret()));
  }

  // Cookieからアクセストークンを取得する
  public Optional<String> getAccessTokenFromCookie(HttpServletRequest request) {
    return Optional.ofNullable(WebUtils.getCookie(request, jwtProperties.getCookieName()))
        // Cookieオブジェクト → jwt
        .map(Cookie::getValue)
        // 空文字をフィルタリング
        .filter(StringUtils::hasText);
  }

  // アクセストークンをCookieとして発行する
  public ResponseCookie generateAccessTokenCookie(Long userId) {
    String jwt = generateAccessToken(userId);

    return ResponseCookie.from(jwtProperties.getCookieName(), jwt)
        // Cookieの適用パス
        .path(jwtProperties.getCookiePath())
        // JavaScriptからのアクセス不可
        .httpOnly(true)
        // 将来的にはHTTPS通信限定にしたい
        // .secure(true)
        // CSRF対策
        .sameSite("Strict")
        // 有効期限
        .maxAge(jwtProperties.getExpiration())
        .build();
  }

  // ログアウト用：Cookieを無効化
  public ResponseCookie getCleanAccessTokenCookie() {
    return ResponseCookie.from(jwtProperties.getCookieName(), "")
        .path(jwtProperties.getCookiePath())
        .httpOnly(true)
        // .secure(true)
        .sameSite("Strict")
        .maxAge(0)
        .build();
  }

  // アクセストークンを検証し、userIdを取得する
  public Optional<Long> getUserIdFromAccessToken(String token) {
    try {
      Claims claims = Jwts.parserBuilder()
          .setSigningKey(key)
          .requireIssuer("auth")
          .requireAudience("api")
          .build()
          .parseClaimsJws(token)
          .getBody();

      if (!"access".equals(claims.get("type"))) {
        throw new JwtException("アクセストークンではありません");
      }

      return Optional.of(Long.valueOf(claims.getSubject()));

    } catch (ExpiredJwtException e) {
      logger.info("トークンの有効期限が切れています");
    } catch (JwtException e) {
      logger.warn("無効なトークンです", e);
    }

    return Optional.empty();
  }

  // アクセストークンを生成
  public String generateAccessToken(Long userId) {
    Date now = new Date();

    return Jwts.builder()
        // 所有者
        .setSubject(String.valueOf(userId))
        // 発行時刻
        .setIssuedAt(now)
        // 有効期限
        .setExpiration(new Date(now.getTime() + jwtProperties.getExpiration()))
        // 発行者
        .setIssuer("auth")
        // 標準クレーム
        .setAudience("api")
        // 種別
        .claim("type", "access")
        // 署名
        .signWith(key, SignatureAlgorithm.HS256)
        // JWT文字列を生成
        .compact();
  }
}