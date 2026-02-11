package com.example.auth.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.auth.model.User;
import com.example.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Spring Securityの {@link UserDetailsService} 実装クラス。
 *
 * <p>
 * Spring Securityの認証フローにおいて、ユーザー名（メールアドレス）から
 * ユーザー情報を取得する役割を担う。{@link org.springframework.security.authentication.AuthenticationManager}
 * が内部的にこのサービスを呼び出す。
 * </p>
 *
 * <p>
 * 呼び出し順序：
 * </p>
 * <ol>
 * <li>AuthController → AuthService.signin() →
 * AuthenticationManager.authenticate()</li>
 * <li>AuthenticationManager → UserDetailsServiceImpl.loadUserByUsername()</li>
 * <li>取得したUserDetailsのパスワードとリクエストのパスワードを
 * {@link org.springframework.security.crypto.password.PasswordEncoder} で比較</li>
 * </ol>
 *
 * <p>
 * {@code @RequiredArgsConstructor} は Lombokアノテーションで、
 * finalフィールド（userRepository）のみを引数に持つコンストラクタを自動生成する。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
  private final UserRepository userRepository;

  /**
   * メールアドレスでユーザー情報を取得し、Spring Security用の {@link UserDetails} に変換する。
   *
   * <p>
   * 処理フロー：
   * </p>
   * <ol>
   * <li>メールアドレスでDBからユーザーを検索</li>
   * <li>見つからなければ {@link UsernameNotFoundException} をスロー（認証失敗）</li>
   * <li>{@link UserDetailsImpl#build(User)} でSpring Security用オブジェクトに変換して返却</li>
   * </ol>
   *
   * @param email ログイン時に入力されたメールアドレス
   * @return ユーザー情報を保持するUserDetails
   * @throws UsernameNotFoundException ユーザーが存在しない場合
   */
  @Override
  public UserDetails loadUserByUsername(String email) {
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new UsernameNotFoundException("ユーザーが存在しません"));

    return UserDetailsImpl.build(user);
  }
}
