package com.bolao.copa.service;

import com.bolao.copa.dto.AuthDtos.AuthResponse;
import com.bolao.copa.dto.AuthDtos.LoginRequest;
import com.bolao.copa.dto.AuthDtos.RegisterRequest;
import com.bolao.copa.dto.AuthDtos.UserResponse;
import com.bolao.copa.entity.User;
import com.bolao.copa.entity.UserRole;
import com.bolao.copa.repository.UserRepository;
import com.bolao.copa.security.JwtService;
import java.util.Locale;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       CurrentUserService currentUserService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        var email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Não foi possível concluir o cadastro com os dados informados.");
        }

        var user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.PARTICIPANTE);
        return authResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        var email = normalizeEmail(request.email());
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password())
        );

        // This also covers the unlikely case in which the account is deleted
        // between authentication and token issuance, without enabling enumeration.
        var user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Credenciais inválidas."));
        return authResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me(UserDetails details) {
        return userResponse(currentUserService.from(details));
    }

    private AuthResponse authResponse(User user) {
        var canonicalRole = user.getRole().canonical();
        return new AuthResponse(
                jwtService.generate(user),
                user.getId(),
                user.getName(),
                user.getEmail(),
                canonicalRole
        );
    }

    private UserResponse userResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().canonical()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
