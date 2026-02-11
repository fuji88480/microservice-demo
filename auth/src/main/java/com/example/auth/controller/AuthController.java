package com.example.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.auth.dto.AuthResult;
import com.example.auth.dto.request.SigninRequest;
import com.example.auth.dto.request.SignupRequest;
import com.example.auth.dto.response.AuthResponse;
import com.example.auth.dto.response.MessageResponse;
import com.example.auth.security.JwtUtils;
import com.example.auth.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 認証関連のRESTコントローラー。
 *
 * <p>
 * ユーザーのサインアップ・サインイン・サインアウト・トークンリフレッシュの
 * 4つのエンドポイントを提供する。すべてのエンドポイントは {@code /api/users} 配下。
 * </p>
 *
 * <p>
 * 認証フロー：
 * </p>
 * <ul>
 * <li>アクセストークン（JWT）はレスポンスボディで返却</li>
 * <li>リフレッシュトークンはHttpOnly Cookieで管理（XSS対策）</li>
 * </ul>
 *
 * <p>
 * ビジネスロジックは {@link AuthService} に委譲し、
 * コントローラーはHTTPリクエスト/レスポンスの変換に専念する。
 * </p>
 *
 * @see AuthService 認証ビジネスロジック
 * @see JwtUtils JWT・Cookie操作ユーティリティ
 */
@RestController
@RequestMapping("/api/users")
public class AuthController {
  private final AuthService authService;
  private final JwtUtils jwtUtils;

  /**
   * コンストラクタインジェクション。
   *
   * @param authService 認証ビジネスロジックを提供するサービス
   * @param jwtUtils    JWT生成・Cookie操作ユーティリティ
   */
  public AuthController(AuthService authService, JwtUtils jwtUtils) {
    this.authService = authService;
    this.jwtUtils = jwtUtils;
  }

  /**
   * ユーザー新規登録（サインアップ）。
   *
   * <p>
   * 処理フロー：
   * </p>
   * <ol>
   * <li>リクエストボディのバリデーション（{@code @Valid}）</li>
   * <li>{@link AuthService#signup} でユーザー作成・トークン生成</li>
   * <li>リフレッシュトークンをHttpOnly Cookieにセット</li>
   * <li>アクセストークンをレスポンスボディで返却（HTTP 201）</li>
   * </ol>
   *
   * @param signupRequest メールアドレスとパスワードを含むリクエスト
   * @return 成功時：201 + AuthResponse / メール重複時：400 + MessageResponse
   */
  @PostMapping("/signup")
  public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest signupRequest) {
    AuthResult result = authService.signup(signupRequest.getEmail(), signupRequest.getPassword());
    ResponseCookie refreshCookie = jwtUtils.generateRefreshTokenCookie(result.refreshToken());

    return ResponseEntity.status(201)
        .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
        .body(new AuthResponse(result.accessToken(), "signup success"));
  }

  /**
   * ユーザーログイン（サインイン）。
   *
   * <p>
   * 処理フロー：
   * </p>
   * <ol>
   * <li>リクエストボディのバリデーション（{@code @Valid}）</li>
   * <li>{@link AuthService#signin} でSpring Securityによる認証 + トークン生成</li>
   * <li>リフレッシュトークンをHttpOnly Cookieにセット</li>
   * <li>アクセストークンをレスポンスボディで返却（HTTP 200）</li>
   * </ol>
   *
   * <p>
   * 認証失敗時はSpring Securityが {@link com.example.auth.security.AuthEntryPointJwt}
   * 経由で
   * 401レスポンスを返すため、このメソッドでは例外ハンドリングは不要。
   * </p>
   *
   * @param signinRequest メールアドレスとパスワードを含むリクエスト
   * @return 成功時：200 + AuthResponse
   */
  @PostMapping("/signin")
  public ResponseEntity<?> signin(@Valid @RequestBody SigninRequest signinRequest) {
    AuthResult result = authService.signin(signinRequest.getEmail(), signinRequest.getPassword());
    ResponseCookie refreshCookie = jwtUtils.generateRefreshTokenCookie(result.refreshToken());

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
        .body(new AuthResponse(result.accessToken(), "login success"));
  }

  /**
   * ユーザーログアウト（サインアウト）。
   *
   * <p>
   * 処理フロー：
   * </p>
   * <ol>
   * <li>Cookieからリフレッシュトークンを取得</li>
   * <li>トークンが存在すればRedisから削除（無効化）</li>
   * <li>クライアント側のCookieも空のCookieで上書きして削除</li>
   * </ol>
   *
   * <p>
   * リフレッシュトークンが存在しない場合でもエラーにせず、正常応答を返す。
   * </p>
   *
   * @param request リフレッシュトークンCookieを含むHTTPリクエスト
   * @return 200 + MessageResponse（常に成功）
   */
  @PostMapping("/signout")
  public ResponseEntity<?> signout(HttpServletRequest request) {
    jwtUtils.getRefreshTokenFromCookie(request)
        .ifPresent(authService::signout);

    ResponseCookie cleanCookie = jwtUtils.getCleanRefreshTokenCookie();

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cleanCookie.toString())
        .body(new MessageResponse("logout success", 200));
  }

  /**
   * アクセストークンのリフレッシュ。
   *
   * <p>
   * 処理フロー：
   * </p>
   * <ol>
   * <li>Cookieからリフレッシュトークンを取得</li>
   * <li>トークンが存在しなければ401を返却</li>
   * <li>{@link AuthService#refresh} でトークンローテーション（旧トークン無効化 + 新トークン発行）</li>
   * <li>新しいリフレッシュトークンをCookieにセット</li>
   * <li>新しいアクセストークンをレスポンスボディで返却</li>
   * </ol>
   *
   * <p>
   * トークンローテーションにより、リフレッシュトークンの再利用を防止している
   * （漏洩時のリスク軽減）。
   * </p>
   *
   * @param request リフレッシュトークンCookieを含むHTTPリクエスト
   * @return 成功時：200 + AuthResponse / 失敗時：401 + MessageResponse
   */
  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(HttpServletRequest request) {
    // Cookieからリフレッシュトークンを取得
    String refreshToken = jwtUtils.getRefreshTokenFromCookie(request)
        .orElse(null);

    // リフレッシュトークンが存在しない場合は401を返却
    if (refreshToken == null) {
      return ResponseEntity.status(401)
          .body(new MessageResponse("リフレッシュトークンがありません", 401));
    }

    // トークンローテーション：旧トークン無効化 + 新トークン発行
    AuthResult result = authService.refresh(refreshToken);
    ResponseCookie newRefreshCookie = jwtUtils.generateRefreshTokenCookie(result.refreshToken());

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, newRefreshCookie.toString())
        .body(new AuthResponse(result.accessToken(), "token refreshed"));
  }
}
