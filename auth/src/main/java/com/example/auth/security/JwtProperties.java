package com.example.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * JWT関連の設定値をapplication.propertiesからバインドするクラス。
 *
 * @Component: Springのコンポーネントとして登録し、DIコンテナで管理する
 * @ConfigurationProperties: 指定したprefixの設定値をフィールドに自動マッピングする
 */
@Component
@ConfigurationProperties(prefix = "microservice-demo.auth.jwt")
@Getter
@Setter
public class JwtProperties {
  /** JWT署名に使う秘密鍵 (Base64エンコード済み) */
  private String secret;

  /** アクセストークンの有効期限 (ミリ秒) - デフォルト15分 */
  private long accessTokenExpiration;

  /** リフレッシュトークンの有効期限 (ミリ秒) - デフォルト7日 */
  private long refreshTokenExpiration;

  /** リフレッシュトークンを格納するCookieの名前 */
  private String refreshCookieName;

  /** Cookieの適用パス */
  private String cookiePath;
}
