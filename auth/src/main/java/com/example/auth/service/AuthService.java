package com.example.auth.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.auth.dto.AuthResult;
import com.example.auth.model.User;
import com.example.auth.repository.UserRepository;
import com.example.auth.security.JwtUtils;
import com.example.auth.security.UserDetailsImpl;

/**
 * 認証ビジネスロジックを提供するサービスクラス。
 *
 * <p>
 * サインアップ・サインイン・サインアウト・トークンリフレッシュの
 * 4つの認証操作を実装する。コントローラーからHTTPの関心事を分離し、
 * 純粋なビジネスロジックに集中する。
 * </p>
 *
 * <p>
 * 依存コンポーネント：
 * </p>
 * <ul>
 * <li>{@link AuthenticationManager} - Spring Securityによるパスワード認証</li>
 * <li>{@link UserRepository} - ユーザーデータのCRUD操作</li>
 * <li>{@link PasswordEncoder} - パスワードのBCryptハッシュ化</li>
 * <li>{@link JwtUtils} - JWTアクセストークンの生成</li>
 * <li>{@link RefreshTokenService} - リフレッシュトークンのRedis管理</li>
 * </ul>
 */
@Service
public class AuthService {

  private final AuthenticationManager authenticationManager;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtils jwtUtils;
  private final RefreshTokenService refreshTokenService;

  public AuthService(
      AuthenticationManager authenticationManager,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtUtils jwtUtils,
      RefreshTokenService refreshTokenService) {
    this.authenticationManager = authenticationManager;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtUtils = jwtUtils;
    this.refreshTokenService = refreshTokenService;
  }

  /**
   * ユーザー新規登録（サインアップ）。
   *
   * <p>
   * 処理フロー：
   * </p>
   * <ol>
   * <li>メールアドレスの重複チェック（existsByEmail）</li>
   * <li>パスワードをBCryptハッシュ化してUserエンティティを作成</li>
   * <li>DBに保存（IDが自動採番される）</li>
   * <li>アクセストークン（JWT）を生成</li>
   * <li>リフレッシュトークン（UUID）を生成し、Redisに保存</li>
   * </ol>
   *
   * @param email    登録メールアドレス
   * @param password 平文パスワード（BCryptハッシュ化して保存）
   * @return アクセストークンとリフレッシュトークンのペア
   * @throws DuplicateEmailException メールアドレスが既に登録済みの場合
   */
  public AuthResult signup(String email, String password) {
    if (userRepository.existsByEmail(email)) {
      throw new DuplicateEmailException(email);
    }

    // パスワードをBCryptハッシュ化してユーザーを作成・保存
    User user = new User(email, passwordEncoder.encode(password));
    User savedUser = userRepository.save(user);

    // アクセストークンとリフレッシュトークンを生成
    String accessToken = jwtUtils.generateAccessToken(savedUser.getId());
    String refreshToken = jwtUtils.generateRefreshToken();
    refreshTokenService.save(refreshToken, savedUser.getId());

    return new AuthResult(accessToken, refreshToken);
  }

  /**
   * ユーザーログイン（サインイン）。
   *
   * <p>
   * 処理フロー：
   * </p>
   * <ol>
   * <li>Spring SecurityのAuthenticationManagerでメール/パスワード認証</li>
   * <li>認証成功時、認証済みユーザー情報（UserDetailsImpl）を取得</li>
   * <li>アクセストークン（JWT）を生成</li>
   * <li>リフレッシュトークン（UUID）を生成し、Redisに保存</li>
   * </ol>
   *
   * <p>
   * パスワードが不正な場合、AuthenticationManagerが
   * {@link org.springframework.security.core.AuthenticationException} をスローし、
   * {@link com.example.auth.security.AuthEntryPointJwt} が401レスポンスを返す。
   * </p>
   *
   * @param email    ログインメールアドレス
   * @param password 平文パスワード
   * @return アクセストークンとリフレッシュトークンのペア
   */
  public AuthResult signin(String email, String password) {
    // Spring Securityによる認証（UserDetailsServiceImpl → PasswordEncoder で検証）
    Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(email, password));

    UserDetailsImpl principal = (UserDetailsImpl) authentication.getPrincipal();

    // 認証成功後、トークンを生成
    String accessToken = jwtUtils.generateAccessToken(principal.getId());
    String refreshToken = jwtUtils.generateRefreshToken();
    refreshTokenService.save(refreshToken, principal.getId());

    return new AuthResult(accessToken, refreshToken);
  }

  /**
   * ユーザーログアウト（サインアウト）。
   *
   * <p>
   * Redisからリフレッシュトークンを削除して無効化する。
   * アクセストークンは短い有効期限で自然に失効するため、明示的な無効化は行わない。
   * </p>
   *
   * @param refreshToken 無効化するリフレッシュトークン
   */
  public void signout(String refreshToken) {
    refreshTokenService.delete(refreshToken);
  }

  /**
   * アクセストークンのリフレッシュ（トークンローテーション）。
   *
   * <p>
   * 処理フロー：
   * </p>
   * <ol>
   * <li>リフレッシュトークンからユーザーIDを取得（Redis参照）</li>
   * <li>トークンローテーション：旧トークン削除 + 新トークン生成・保存</li>
   * <li>新しいアクセストークン（JWT）を生成</li>
   * </ol>
   *
   * <p>
   * トークンローテーションにより、リフレッシュトークンは1回限り使用可能。
   * 漏洩したトークンが再利用された場合に検知できるセキュリティ対策。
   * </p>
   *
   * @param refreshToken 現在のリフレッシュトークン
   * @return 新しいアクセストークンとリフレッシュトークンのペア
   * @throws InvalidRefreshTokenException リフレッシュトークンが無効（存在しない or 期限切れ）の場合
   */
  public AuthResult refresh(String refreshToken) {
    Long userId = refreshTokenService.findUserIdByToken(refreshToken)
        .orElseThrow(() -> new InvalidRefreshTokenException());

    // トークンローテーション：旧トークン削除 + 新トークン発行
    String newRefreshToken = refreshTokenService.rotate(refreshToken, userId);
    String newAccessToken = jwtUtils.generateAccessToken(userId);

    return new AuthResult(newAccessToken, newRefreshToken);
  }

  /**
   * メールアドレス重複時にスローされる例外。
   *
   * <p>
   * サインアップ時に同じメールアドレスのユーザーが既に存在する場合に使用。
   * コントローラーでキャッチして400 Bad Requestレスポンスに変換される。
   * </p>
   */
  public static class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String email) {
      super("ユーザーが既に存在します: " + email);
    }
  }

  /**
   * 無効なリフレッシュトークン時にスローされる例外。
   *
   * <p>
   * リフレッシュトークンがRedis上に存在しない（期限切れ or 既に使用済み）場合に使用。
   * コントローラーでキャッチして401 Unauthorizedレスポンスに変換される。
   * </p>
   */
  public static class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
      super("無効なリフレッシュトークンです");
    }
  }
}
