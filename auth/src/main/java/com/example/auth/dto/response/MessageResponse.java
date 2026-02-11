package com.example.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 汎用メッセージレスポンスDTO。
 *
 * <p>認証失敗時やサインアウト成功時など、データ本体を持たない
 * レスポンスに使用する。メッセージとHTTPステータスコードのペアを返す。</p>
 *
 * <p>Lombokアノテーション：</p>
 * <ul>
 *   <li>{@code @Getter/@Setter} - アクセサメソッドを自動生成</li>
 *   <li>{@code @AllArgsConstructor} - 全フィールドを引数に持つコンストラクタを自動生成</li>
 *   <li>{@code @NoArgsConstructor} - 引数なしコンストラクタを自動生成（Jacksonデシリアライズに必要）</li>
 * </ul>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MessageResponse {
  private String message;
  private int statusCode;
}
