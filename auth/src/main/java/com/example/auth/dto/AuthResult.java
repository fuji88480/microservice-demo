package com.example.auth.dto;

/**
 * 認証結果を保持するDTO（Data Transfer Object）。
 *
 * <p>
 * サインアップ・サインイン・トークンリフレッシュの結果として、
 * {@link com.example.auth.service.AuthService} からコントローラーへ返却される。
 * </p>
 *
 * <p>
 * Java {@code record} を使用することで以下が自動生成される：
 * </p>
 * <ul>
 * <li>コンストラクタ（全フィールド）</li>
 * <li>アクセサメソッド（{@code accessToken()}, {@code refreshToken()}）</li>
 * <li>{@code equals()}, {@code hashCode()}, {@code toString()}</li>
 * </ul>
 * <p>
 * 全フィールドはfinalかつImmutable（不変）であり、スレッドセーフ。
 * </p>
 *
 * @param accessToken  JWTアクセストークン（レスポンスボディで返却、短い有効期限）
 * @param refreshToken リフレッシュトークン（HttpOnly Cookieで管理、Redis上で有効期限管理）
 */
public record AuthResult(String accessToken, String refreshToken) {
}
