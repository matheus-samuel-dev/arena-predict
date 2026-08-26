package com.bolao.copa.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bolao.copa.entity.*;
import com.bolao.copa.repository.UserRepository;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder passwords;
    @Mock TransactionTemplate transactions;

    @Test
    void startupNeverResetsPasswordOfExistingDemoAccount() throws Exception {
        User admin = user("admin@arenapredict.com", "Administrador Demo", "custom-bcrypt-hash", UserRole.ADMIN);
        when(users.findAllByRole(UserRole.USER)).thenReturn(List.of());
        when(users.findByEmailIgnoreCase(anyString())).thenAnswer(invocation -> {
            String email = invocation.getArgument(0);
            return email.equalsIgnoreCase(admin.getEmail()) ? Optional.of(admin) : Optional.empty();
        });
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwords.encode(anyString())).thenReturn("new-demo-hash");
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());

        new DataInitializer().seed(users, passwords, transactions).run();

        assertThat(admin.getPasswordHash()).isEqualTo("custom-bcrypt-hash");
        verify(passwords, never()).encode("Admin@123");
    }

    private User user(String email, String name, String passwordHash, UserRole role) {
        User value = new User();
        value.setEmail(email);
        value.setName(name);
        value.setPasswordHash(passwordHash);
        value.setRole(role);
        return value;
    }
}
