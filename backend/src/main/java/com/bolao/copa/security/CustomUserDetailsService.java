package com.bolao.copa.security;

import com.bolao.copa.repository.UserRepository;
import java.util.LinkedHashSet;
import java.util.Locale;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var normalizedEmail = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        var user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas."));

        var canonicalRole = user.getRole().canonical();
        var authorities = new LinkedHashSet<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority(canonicalRole.authority()));
        if (canonicalRole.name().equals("PARTICIPANTE")) {
            // Compatibility authorities for guards that still use an older role name.
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            authorities.add(new SimpleGrantedAuthority("ROLE_PLAYER"));
        }

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                authorities
        );
    }
}
