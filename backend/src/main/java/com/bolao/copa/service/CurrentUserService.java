package com.bolao.copa.service;

import com.bolao.copa.entity.User;
import com.bolao.copa.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User from(UserDetails details) {
        if (details == null) {
            throw new IllegalStateException("Sessão autenticada não encontrada.");
        }
        return userRepository.findByEmailIgnoreCase(details.getUsername())
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));
    }
}
