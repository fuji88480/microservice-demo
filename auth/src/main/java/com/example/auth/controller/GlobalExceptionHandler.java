package com.example.auth.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.auth.dto.response.MessageResponse;
import com.example.auth.security.JwtUtils;
import com.example.auth.service.AuthService;

/**
 * グローバル例外ハンドラー。
 *
 * <p>
 * コントローラー全体で発生する例外をキャッチし、
 * 統一された {@link MessageResponse} 形式でレスポンスを返却する。
 * </p>
 *
 * <p>
 * ハンドリング対象：
 * </p>
 * <ul>
 * <li>{@link MethodArgumentNotValidException} - 400 Bad
 * Request（バリデーションエラー）</li>
 * <li>{@link AuthService.DuplicateEmailException} - 400 Bad Request</li>
 * <li>{@link AuthService.InvalidRefreshTokenException} - 401 Unauthorized</li>
 * <li>{@link Exception} - 500 Internal Server Error（DB障害等の予期しないエラー）</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private final JwtUtils jwtUtils;

  public GlobalExceptionHandler(JwtUtils jwtUtils) {
    this.jwtUtils = jwtUtils;
  }

  /**
   * バリデーションエラー時の例外ハンドリング。
   *
   * <p>
   * {@code @Valid} によるBean Validationが失敗した場合にスローされる。
   * 全フィールドエラーのメッセージを結合してクライアントに返却する。
   * </p>
   *
   * @param e MethodArgumentNotValidException
   * @return 400 Bad Request + MessageResponse
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<MessageResponse> handleValidationException(MethodArgumentNotValidException e) {
    String errorMessage = e.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .collect(Collectors.joining(", "));
    return ResponseEntity.badRequest()
        .body(new MessageResponse(errorMessage, 400));
  }

  /**
   * メールアドレス重複時の例外ハンドリング。
   *
   * @param e DuplicateEmailException
   * @return 400 Bad Request + MessageResponse
   */
  @ExceptionHandler(AuthService.DuplicateEmailException.class)
  public ResponseEntity<MessageResponse> handleDuplicateEmail(AuthService.DuplicateEmailException e) {
    return ResponseEntity.badRequest()
        .body(new MessageResponse("ユーザーが既に存在します", 400));
  }

  /**
   * 無効なリフレッシュトークン時の例外ハンドリング。
   *
   * @param e InvalidRefreshTokenException
   * @return 401 Unauthorized + MessageResponse
   */
  @ExceptionHandler(AuthService.InvalidRefreshTokenException.class)
  public ResponseEntity<MessageResponse> handleInvalidRefreshToken(AuthService.InvalidRefreshTokenException e) {
    ResponseCookie cleanCookie = jwtUtils.getCleanRefreshTokenCookie();
    return ResponseEntity.status(401)
        .header(HttpHeaders.SET_COOKIE, cleanCookie.toString())
        .body(new MessageResponse("無効なリフレッシュトークンです", 401));
  }

  /**
   * 予期しない例外のハンドリング（DB障害、Redis障害等）。
   *
   * <p>
   * 内部エラーの詳細はログに記録し、クライアントには汎用メッセージのみ返却する
   * （情報漏洩防止）。
   * </p>
   *
   * @param e 予期しない例外
   * @return 500 Internal Server Error + MessageResponse
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<MessageResponse> handleUnexpectedException(Exception e) {
    logger.error("予期しないエラーが発生しました", e);
    return ResponseEntity.status(500)
        .body(new MessageResponse("サーバー内部エラーが発生しました", 500));
  }

  @ExceptionHandler(AuthService.UserNotFoundException.class)
  public ResponseEntity<MessageResponse> handleUserNotFound(AuthService.UserNotFoundException e) {
    return ResponseEntity.status(404)
        .body(new MessageResponse("ユーザーが見つかりません", 404));
  }
}
