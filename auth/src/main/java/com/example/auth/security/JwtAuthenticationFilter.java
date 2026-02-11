package com.example.auth.security;

import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 全HTTPリクエストに対してJWT認証を行うフィルタ。
 * Authorizationヘッダーからアクセストークンを取得し、
 * 有効であればSecurityContextに認証情報をセットする。
 *
 * 【Spring Securityのフィルタチェーン】
 * HTTPリクエスト → [JwtAuthenticationFilter] →
 * [UsernamePasswordAuthenticationFilter] → ... → Controller
 *
 * WebSecurityConfigで addFilterBefore() を使い、
 * UsernamePasswordAuthenticationFilter の前にこのフィルタを挿入している。
 *
 * @Component: Springのコンポーネントとして登録。WebSecurityConfigでDIされる。
 *
 *             OncePerRequestFilter を継承:
 *             - 1リクエストにつき1回だけ実行されることを保証するSpringの抽象フィルタ
 *             - サーブレットのフォワードやインクルードで複数回呼ばれることを防ぐ
 *             - doFilterInternal() メソッドをオーバーライドして認証ロジックを実装する
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

  private final JwtUtils jwtUtils;

  public JwtAuthenticationFilter(JwtUtils jwtUtils) {
    this.jwtUtils = jwtUtils;
  }

  /**
   * リクエスト毎のJWT認証処理。
   *
   * 【処理フロー】
   * 1. Authorizationヘッダーから "Bearer <token>" 形式でアクセストークンを取得
   * 2. トークンを検証し、ユーザーIDを抽出
   * 3. SecurityContextに認証情報をセット
   * 4. 次のフィルタへ処理を委譲
   *
   * 【SecurityContextとは】
   * Spring Securityがリクエスト中の認証情報を保持するスレッドローカルな入れ物。
   * ここに認証情報がセットされていると、.authenticated() で保護されたエンドポイントにアクセスできる。
   * セットされていないと、AuthEntryPointJwt が呼ばれて401が返る。
   *
   * @param httpServletRequest  HTTPリクエスト
   * @param httpServletResponse HTTPレスポンス
   * @param filterChain         フィルタチェーン (次のフィルタに処理を渡す)
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse,
      FilterChain filterChain)
      throws ServletException, IOException {
    try {
      // 1. Authorizationヘッダーからアクセストークンを取得
      // 形式: "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
      // "Bearer " プレフィックスを除去してJWT文字列のみを取得
      Optional<String> accessTokenOpt = jwtUtils.getAccessTokenFromHeader(httpServletRequest);

      if (accessTokenOpt.isPresent()) {
        String accessToken = accessTokenOpt.get();

        // 2. アクセストークンを検証し、ユーザーIDを取得
        // 署名検証、有効期限チェック、issuer/audience/typeの検証を行う
        Optional<Long> userIdOpt = jwtUtils.getUserIdFromAccessToken(accessToken);

        if (userIdOpt.isPresent()) {
          Long userId = userIdOpt.get();

          // 3. UsernamePasswordAuthenticationToken を作成
          // - principal (第1引数): ユーザーID - 認証されたユーザーの識別子
          // - credentials (第2引数): null - パスワードはトークン認証では不要
          // - authorities (第3引数): null - ロールなしの設計
          UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(userId,
              null, null);

          // リクエスト固有の詳細情報を付加 (IPアドレス、セッションID等)
          authenticationToken.setDetails(
              new WebAuthenticationDetailsSource().buildDetails(httpServletRequest));

          // 4. SecurityContextに認証情報をセット
          // これにより、このリクエストは「認証済み」として扱われる
          // Controller内で SecurityContextHolder.getContext().getAuthentication() で取得可能
          SecurityContextHolder.getContext()
              .setAuthentication(authenticationToken);
        }
      }
    } catch (Exception e) {
      // 認証処理中の予期しないエラーはログに記録し、
      // リクエスト自体は未認証として続行させる (401は後段のフィルタが返す)
      logger.warn("トークンの認証に失敗しました", e);
    }

    // 5. 次のフィルタに処理を委譲 (必ず呼ぶ。呼ばないとリクエストが止まる)
    filterChain.doFilter(httpServletRequest, httpServletResponse);
  }
}
