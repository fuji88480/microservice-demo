package com.example.auth.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.example.auth.dto.response.MessageResponse;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * JWT認証失敗時のエントリーポイント。
 *
 * <p>
 * Spring Securityの {@link AuthenticationEntryPoint} を実装し、
 * 認証されていないリクエストに対してJSON形式の401レスポンスを返す。
 * </p>
 *
 * <p>
 * このクラスが呼ばれるケース：
 * </p>
 * <ul>
 * <li>JWTトークンが付与されていないリクエストが認証必須エンドポイントにアクセスした場合</li>
 * <li>JWTトークンの検証に失敗した場合（期限切れ、改ざん等）</li>
 * <li>サインイン時のパスワードが不正な場合</li>
 * </ul>
 *
 * <p>
 * {@link WebSecurityConfig} で {@code exceptionHandling} に登録されている。
 * </p>
 */
@Component
public class AuthEntryPointJwt implements AuthenticationEntryPoint {
  private static final Logger logger = LoggerFactory.getLogger(AuthEntryPointJwt.class);

  /** Javaオブジェクト → JSON変換に使用するObjectMapper */
  private final ObjectMapper objectMapper;

  public AuthEntryPointJwt(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * 認証失敗時にJSON形式のエラーレスポンスを返却する。
   *
   * <p>
   * 処理フロー：
   * </p>
   * <ol>
   * <li>認証失敗をログ出力（WARN レベル）</li>
   * <li>レスポンスのステータスコードを401に設定</li>
   * <li>Content-Typeをapplication/jsonに設定</li>
   * <li>エラー詳細（ステータス、エラーメッセージ、リクエストパス）をJSON形式で書き出し</li>
   * </ol>
   *
   * @param httpServletRequest  認証に失敗したHTTPリクエスト
   * @param httpServletResponse エラーレスポンスを書き込むHTTPレスポンス
   * @param authException       認証失敗の原因を表す例外
   */
  @Override
  public void commence(
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse,
      AuthenticationException authException)
      throws IOException {
    logger.warn("認証に失敗しました：{}", authException.getMessage());

    httpServletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    httpServletResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);

    MessageResponse body = new MessageResponse("認証に失敗しました", HttpServletResponse.SC_UNAUTHORIZED);
    objectMapper.writeValue(httpServletResponse.getOutputStream(), body);
  }
}
