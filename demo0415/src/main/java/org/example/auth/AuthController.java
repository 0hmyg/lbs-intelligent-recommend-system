package org.example.auth;

import org.example.auth.dto.AuthResponse;
import org.example.auth.dto.LoginRequest;
import org.example.auth.dto.RegisterRequest;
import org.example.auth.dto.UserMeResponse;
import org.example.common.ApiResponse;
import org.example.domain.User;
import org.example.geo.GeoUtils;
import org.example.repo.UserRepository;
import org.example.service.UserProfileService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserProfileService userProfileService;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil,
                          UserProfileService userProfileService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userProfileService = userProfileService;
    }

    @PostMapping("/register")
    public ApiResponse<UserMeResponse> register(@Valid @RequestBody RegisterRequest req) {
        String username = req.getUsername() == null ? "" : req.getUsername().trim();
        String nickname = req.getNickname() == null ? "" : req.getNickname().trim();
        String phone = req.getPhone() == null ? null : req.getPhone().trim();
        if (!req.getPassword().equals(req.getConfirmPassword())) {
            throw new IllegalArgumentException("两次密码不一致");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }

        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        u.setNickname(nickname);
        u.setPhone(phone);
        u.setRole("user");
        u.setStatus((short) 1);
        u.setCreatedAt(java.time.LocalDateTime.now());
        u.setUpdatedAt(java.time.LocalDateTime.now());
        u.setLocationName("北京市东城区");
        u.setLocationGeom(GeoUtils.point(116.407526, 39.90403));
        User saved = userRepository.save(u);
        userProfileService.createDefaultProfileAsync(saved.getId());
        return ApiResponse.ok(toMe(saved));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String token = jwtUtil.generateToken(principal);

        User user = userRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        return ApiResponse.ok(new AuthResponse(token, toMe(user)));
    }

    @GetMapping("/me")
    public ApiResponse<UserMeResponse> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal)) {
            throw new IllegalArgumentException("未登录");
        }
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Optional<User> userOpt = userRepository.findByUsername(principal.getUsername());
        User user = userOpt.orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return ApiResponse.ok(toMe(user));
    }

    private static UserMeResponse toMe(User user) {
        UserMeResponse out = new UserMeResponse();
        out.setId(user.getId());
        out.setUsername(user.getUsername());
        out.setNickname(user.getNickname());
        out.setAvatarUrl(user.getAvatarUrl());
        out.setRole(user.getRole());
        out.setLocationName(user.getLocationName());
        if (user.getLocationGeom() != null) {
            out.setLng(user.getLocationGeom().getX());
            out.setLat(user.getLocationGeom().getY());
        }
        return out;
    }
}
