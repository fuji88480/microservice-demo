package com.example.auth.security.jwt;

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

// リクエスト毎に一回だけ実行
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

  private final JwtUtils jwtUtils;

  public JwtAuthenticationFilter(JwtUtils jwtUtils) {
    this.jwtUtils = jwtUtils;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse,
      FilterChain filterChain)
      throws ServletException, IOException {
    try {
      Optional<String> accessTokenOpt = jwtUtils.getAccessTokenFromCookie(httpServletRequest);

      if (accessTokenOpt.isPresent()) {
        String accessToken = accessTokenOpt.get();

        // アクセストークンを検証し、userIdを取得する
        Optional<Long> userIdOpt = jwtUtils.getUserIdFromAccessToken(accessToken);

        if (userIdOpt.isPresent()) {
          Long userId = userIdOpt.get();

          // 認証トークン作成
          UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(userId,
              null, null);
          authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(httpServletRequest));

          // SecurityContextにセット
          SecurityContextHolder.getContext()
              .setAuthentication(authenticationToken);
        }
      }
    } catch (Exception e) {
      logger.warn("トークンの認証に失敗しました", e);
    }

    filterChain.doFilter(httpServletRequest, httpServletResponse);
  }
}
