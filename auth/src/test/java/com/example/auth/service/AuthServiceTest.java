package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.auth.dto.AuthResult;
import com.example.auth.model.User;
import com.example.auth.repository.UserRepository;
import com.example.auth.security.JwtUtils;
import com.example.auth.security.UserDetailsImpl;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock
  private AuthenticationManager authenticationManager;

  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private JwtUtils jwtUtils;

  @Mock
  private RefreshTokenService refreshTokenService;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService = new AuthService(
        authenticationManager, userRepository, passwordEncoder, jwtUtils, refreshTokenService);
  }

  @Nested
  @DisplayName("signup - ユーザー登録")
  class SignupTest {

    @Test
    @DisplayName("正常系: ユーザーを保存しトークンを返す")
    void shouldSaveUserAndReturnTokens() {
      String email = "test@example.com";
      String password = "password123";
      String encodedPassword = "$2a$10$encoded";
      Long userId = 1L;

      when(userRepository.existsByEmail(email)).thenReturn(false);
      when(passwordEncoder.encode(password)).thenReturn(encodedPassword);

      User savedUser = new User(email, encodedPassword);
      // Use reflection to set ID since there's no setter
      try {
        var idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(savedUser, userId);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      when(userRepository.save(any(User.class))).thenReturn(savedUser);
      when(jwtUtils.generateAccessToken(userId)).thenReturn("access-token");
      when(jwtUtils.generateRefreshToken()).thenReturn("refresh-token");

      AuthResult result = authService.signup(email, password);

      assertThat(result.accessToken()).isEqualTo("access-token");
      assertThat(result.refreshToken()).isEqualTo("refresh-token");
      verify(userRepository).save(any(User.class));
      verify(refreshTokenService).save("refresh-token", userId);
    }

    @Test
    @DisplayName("異常系: メールアドレスが重複している場合はDuplicateEmailExceptionをスロー")
    void shouldThrowWhenEmailAlreadyExists() {
      String email = "existing@example.com";

      when(userRepository.existsByEmail(email)).thenReturn(true);

      assertThatThrownBy(() -> authService.signup(email, "password123"))
          .isInstanceOf(AuthService.DuplicateEmailException.class);

      verify(userRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("signin - ログイン")
  class SigninTest {

    @Test
    @DisplayName("正常系: 認証成功でトークンを返す")
    void shouldAuthenticateAndReturnTokens() {
      String email = "test@example.com";
      String password = "password123";
      Long userId = 1L;

      Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
      UserDetailsImpl principal = UserDetailsImpl.build(createUserWithId(userId, email));

      when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
          .thenReturn(authentication);
      when(authentication.getPrincipal()).thenReturn(principal);
      when(jwtUtils.generateAccessToken(userId)).thenReturn("access-token");
      when(jwtUtils.generateRefreshToken()).thenReturn("refresh-token");

      AuthResult result = authService.signin(email, password);

      assertThat(result.accessToken()).isEqualTo("access-token");
      assertThat(result.refreshToken()).isEqualTo("refresh-token");
      verify(refreshTokenService).save("refresh-token", userId);
    }

    @Test
    @DisplayName("異常系: 認証失敗でBadCredentialsExceptionがスローされる")
    void shouldThrowWhenAuthenticationFails() {
      when(authenticationManager.authenticate(any()))
          .thenThrow(new BadCredentialsException("Bad credentials"));

      assertThatThrownBy(() -> authService.signin("test@example.com", "wrong"))
          .isInstanceOf(BadCredentialsException.class);
    }
  }

  @Nested
  @DisplayName("signout - ログアウト")
  class SignoutTest {

    @Test
    @DisplayName("正常系: リフレッシュトークンを削除する")
    void shouldDeleteRefreshToken() {
      String refreshToken = "token-to-delete";

      authService.signout(refreshToken);

      verify(refreshTokenService).delete(refreshToken);
    }
  }

  @Nested
  @DisplayName("refresh - トークンリフレッシュ")
  class RefreshTest {

    @Test
    @DisplayName("正常系: トークンローテーションで新しいトークンを返す")
    void shouldRotateAndReturnNewTokens() {
      String oldToken = "old-refresh-token";
      Long userId = 1L;

      when(refreshTokenService.findUserIdByToken(oldToken)).thenReturn(Optional.of(userId));
      when(refreshTokenService.rotate(oldToken, userId)).thenReturn("new-refresh-token");
      when(jwtUtils.generateAccessToken(userId)).thenReturn("new-access-token");

      AuthResult result = authService.refresh(oldToken);

      assertThat(result.accessToken()).isEqualTo("new-access-token");
      assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
    }

    @Test
    @DisplayName("異常系: 無効なリフレッシュトークンでInvalidRefreshTokenExceptionをスロー")
    void shouldThrowWhenRefreshTokenIsInvalid() {
      String invalidToken = "invalid-token";

      when(refreshTokenService.findUserIdByToken(invalidToken)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> authService.refresh(invalidToken))
          .isInstanceOf(AuthService.InvalidRefreshTokenException.class);
    }
  }

  private User createUserWithId(Long id, String email) {
    User user = new User(email, "hashed");
    try {
      var idField = User.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(user, id);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return user;
  }
}
