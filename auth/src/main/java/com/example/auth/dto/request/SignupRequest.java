package com.example.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * サインアップ（新規登録）リクエストDTO。
 *
 * <p>クライアントからの新規登録リクエストボディをバインドする。
 * サインインよりも厳密なバリデーション（メール形式チェック、パスワード長制約）を適用する。</p>
 *
 * <p>Lombokアノテーション：</p>
 * <ul>
 *   <li>{@code @Getter} - 全フィールドのgetterメソッドを自動生成</li>
 *   <li>{@code @Setter} - 全フィールドのsetterメソッドを自動生成（Jacksonデシリアライズに必要）</li>
 * </ul>
 */
@Getter
@Setter
public class SignupRequest {
  /**
   * 登録用メールアドレス。
   * <ul>
   *   <li>{@code @NotBlank} - 空文字・nullを拒否</li>
   *   <li>{@code @Size(max = 50)} - 最大50文字（DBカラム長に合わせた制約）</li>
   *   <li>{@code @Email} - メールアドレス形式のバリデーション</li>
   * </ul>
   */
  @NotBlank
  @Size(max = 50)
  @Email
  private String email;

  /**
   * 登録用パスワード（平文、サーバー側でBCryptハッシュ化される）。
   * <ul>
   *   <li>{@code @NotBlank} - 空文字・nullを拒否</li>
   *   <li>{@code @Size(min = 6, max = 40)} - 6〜40文字の長さ制約（セキュリティ要件）</li>
   * </ul>
   */
  @NotBlank
  @Size(min = 6, max = 40)
  private String password;
}
