package com.example.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * サインイン（ログイン）リクエストDTO。
 *
 * <p>
 * クライアントからのログインリクエストボディをバインドする。
 * {@code @Valid} によるバリデーションで空文字・nullを拒否する。
 * </p>
 *
 * <p>
 * Lombokアノテーション：
 * </p>
 * <ul>
 * <li>{@code @Getter} - 全フィールドのgetterメソッドを自動生成</li>
 * <li>{@code @Setter} - 全フィールドのsetterメソッドを自動生成（Jacksonデシリアライズに必要）</li>
 * </ul>
 */
@Getter
@Setter
public class SigninRequest {
  /** ログイン用メールアドレス。{@code @NotBlank} により空文字・nullを拒否。 */
  @NotBlank
  private String email;

  /** ログイン用パスワード（平文）。{@code @NotBlank} により空文字・nullを拒否。 */
  @NotBlank
  private String password;
}
