package com.example.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.auth.dto.request.SigninRequest;
import com.example.auth.dto.request.SignupRequest;
import com.example.auth.dto.response.MessageResponse;
import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import com.example.auth.security.jwt.JwtUtils;
import com.example.auth.security.service.UserDetailsImpl;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class AuthController {
  private final AuthenticationManager authenticationManager;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtils jwtUtils;

  public AuthController(AuthenticationManager authenticationManager, UserRepository userRepository,
      PasswordEncoder passwordEncoder, JwtUtils jwtUtils) {
    this.authenticationManager = authenticationManager;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtUtils = jwtUtils;
  }

  /**
   * signup
   */
  @PostMapping("/signup")
  public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest signupRequest) {
    if (userRepository.existsByEmail(signupRequest.getEmail())) {
      return ResponseEntity.badRequest()
          .body(new MessageResponse("ユーザーが既に存在します", 400));
    }

    User user = new User(
        signupRequest.getEmail(),
        passwordEncoder.encode(signupRequest.getPassword()));
    userRepository.save(user);

    Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(signupRequest.getEmail(), signupRequest.getPassword()));

    UserDetailsImpl principal = (UserDetailsImpl) authentication.getPrincipal();

    ResponseCookie accessTokenCookie = jwtUtils.generateAccessTokenCookie(principal.getId());

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, accessTokenCookie.toString())
        .body(new MessageResponse("signup success", 200));
  }

  /**
   * signin
   */
  @PostMapping("/signin")
  public ResponseEntity<?> signin(@Valid @RequestBody SigninRequest signinRequest) {
    Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(signinRequest.getEmail(), signinRequest.getPassword()));

    UserDetailsImpl principal = (UserDetailsImpl) authentication.getPrincipal();

    ResponseCookie accessTokenCookie = jwtUtils.generateAccessTokenCookie(principal.getId());

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, accessTokenCookie.toString())
        .body(new MessageResponse("login success", 200));
  }

  /**
   * signout
   */
  @PostMapping("/signout")
  public ResponseEntity<?> signout() {
    ResponseCookie cleanCookie = jwtUtils.getCleanAccessTokenCookie();

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cleanCookie.toString())
        .body(new MessageResponse("logout success", 200));
  }
}