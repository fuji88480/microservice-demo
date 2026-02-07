package com.example.auth.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
// 設定ファイルをオブジェクトとしてバインド
@ConfigurationProperties(prefix = "microservice-demo.auth.jwt")
@Getter
@Setter
public class JwtProperties {
  private String secret;
  private long expiration;
  private String cookieName;
  private String cookiePath;
}
