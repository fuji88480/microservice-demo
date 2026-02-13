package com.example.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.auth.security.AuthEntryPointJwt;
import com.example.auth.security.JwtAuthenticationFilter;

/**
 * Spring Securityの設定クラス。
 *
 * <p>
 * JWT認証ベースのステートレスセキュリティを構成する。
 * セッションを使用せず、リクエスト毎にJWTを検証する方式を採用。
 * </p>
 *
 * <p>
 * アノテーション：
 * </p>
 * <ul>
 * <li>{@code @Configuration} - Spring Bean定義クラスとしてマーク</li>
 * <li>{@code @EnableWebSecurity} - Spring Security のWebセキュリティ機能を有効化</li>
 * <li>{@code @EnableMethodSecurity} - {@code @PreAuthorize}
 * 等のメソッドレベルセキュリティを有効化</li>
 * </ul>
 *
 * @see JwtAuthenticationFilter リクエスト毎のJWT検証フィルター
 * @see AuthEntryPointJwt 認証失敗時のエラーレスポンスハンドラー
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {
  private final AuthEntryPointJwt authEntryPointJwt;
  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  public WebSecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, AuthEntryPointJwt authEntryPointJwt) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.authEntryPointJwt = authEntryPointJwt;
  }

  /**
   * AuthenticationManagerをBean登録する。
   *
   * <p>
   * Spring Securityの {@link AuthenticationConfiguration} から取得したデフォルトの
   * AuthenticationManagerをそのまま公開する。{@link com.example.auth.service.AuthService} が
   * サインイン時のパスワード認証に使用する。
   * </p>
   *
   * <p>
   * 内部的には {@link com.example.auth.security.UserDetailsServiceImpl} と
   * {@link #passwordEncoder()} を使ってユーザー認証を行う。
   * </p>
   */
  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
      throws Exception {
    return authenticationConfiguration.getAuthenticationManager();
  }

  /**
   * パスワードエンコーダーをBean登録する。
   *
   * <p>
   * BCryptアルゴリズムを使用。ソルト（ランダム値）が自動付与されるため、
   * 同じパスワードでも異なるハッシュ値が生成される（レインボーテーブル攻撃対策）。
   * デフォルトのストレングス（10）を使用。
   * </p>
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * セキュリティフィルターチェインを構成する。
   *
   * <p>
   * HTTPリクエストに対するセキュリティルールを定義する。
   * JWT認証ベースのステートレス構成のため、CSRF保護とセッション管理を無効化している。
   * </p>
   *
   * @param http HttpSecurityビルダー
   * @return 構成済みのSecurityFilterChain
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // CSRF保護を無効化：JWT認証ではCookieベースのセッションを使わないため不要。
        // リフレッシュトークンCookieはHttpOnly属性で保護している。
        .csrf(csrf -> csrf.disable())
        // ステートレス設定：サーバー側でセッションを作成・保持しない。
        // 認証状態はJWTトークンで管理するため、スケーラビリティが向上する。
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // 認証失敗時のハンドラ：AuthEntryPointJwtがJSON形式の401レスポンスを返す
        .exceptionHandling(exeption -> exeption.authenticationEntryPoint(authEntryPointJwt))
        // 認可ルール
        .authorizeHttpRequests(auth -> auth
            // /api/users/signup(サインアップ), /api/users/signin(サインイン)は認証不要
            .requestMatchers("/api/users/signup", "/api/users/signin").permitAll()
            // /api/tickets/show(購入可能チケット一覧取得)は認証不要
            // .requestMatchers("/api/tickets/show").permitAll()
            // /api/users, /api/tickets, /api/orders, /api/paymentsは認証要
            .requestMatchers("/api/users/**", "/api/tickets/**", "/api/orders/**", "/api/payments/**").authenticated()
            .anyRequest().denyAll());

    // JwtAuthenticationFilterをUsernamePasswordAuthenticationFilterの前に配置。
    // これにより、リクエスト毎にJWTを検証し、SecurityContextに認証情報をセットする。
    http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    // HttpSecurityに設定した内容を確定させて、SecurityFilterChainオブジェクトを生成する
    return http.build();
  }

}