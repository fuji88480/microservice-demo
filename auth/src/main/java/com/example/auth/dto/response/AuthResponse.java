package com.example.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 認証成功時のレスポンスDto
 *
 * - フロントエンドがJavaScriptのメモリ上(変数)にトークンを保持
 * - APIリクエスト時にAuthorizationヘッダーとして付与: "Bearer <token>"
 * - ページリロード時はリフレッシュトークン(Cookie)で再取得
 *
 * @AllArgsConstructor: 全フィールドを引数に持つコンストラクタを自動生成
 * @Getter: JacksonがJSON変換時にgetterを使ってフィールド値を取得する
 */
@Getter
@AllArgsConstructor
public class AuthResponse {
  private String accessToken;
  private String message;
}
