package com.smartlib;

import com.smartlib.config.AppProperties;
import com.smartlib.config.JwtService;
import com.smartlib.dto.AuthResponse;
import com.smartlib.dto.LoginDto;
import com.smartlib.dto.RegisterDto;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.model.Role;
import com.smartlib.model.User;
import com.smartlib.repository.UserRepository;
import com.smartlib.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UserRepository userRepository;
    private AuthService authService;
    private JwtService jwtService;

    private final String secret = "unit-test-secret-0123456789abc-unit-test-secret";

    @BeforeEach
    void setup() {
        userRepository = mock(UserRepository.class);
        AppProperties app = new AppProperties();
        app.getJwt().setSecret(secret);
        jwtService = new JwtService(app);
        authService = new AuthService(userRepository, new BCryptPasswordEncoder(), jwtService);
    }

    @Test
    void jwtService_rejectsShortSecret() {
        AppProperties shortApp = new AppProperties();
        shortApp.getJwt().setSecret("too-short");

        assertThatThrownBy(() -> new JwtService(shortApp))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET is too short");
    }

    @Test
    void jwtService_rejectsMissingSecret() {
        AppProperties noSecret = new AppProperties();
        noSecret.getJwt().setSecret(null);

        assertThatThrownBy(() -> new JwtService(noSecret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET is not set");
    }

    private RegisterDto register(String role) {
        RegisterDto dto = new RegisterDto();
        dto.setName("New Student");
        dto.setEmail("new@smartlib.test");
        dto.setPassword("Password123");
        dto.setPreferredSubjects(List.of("Physics"));
        return dto;
    }

    @Test
    void register_student_createdWithHashedPasswordAndStudentRole() {
        when(userRepository.findByEmail("new@smartlib.test")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("u1");
            return u;
        });

        AuthResponse response = authService.register(register("LIBRARIAN"));

        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getUser().getRole()).isEqualTo(Role.STUDENT); // role forced server-side
        assertThat(response.getUser().getPreferredSubjects()).containsExactly("Physics");

        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).startsWith("$2"); // BCrypt hash, not plaintext
        assertThat(captor.getValue().getPasswordHash()).doesNotContain("Password123");
    }

    @Test
    void register_duplicateEmail_conflict() {
        when(userRepository.findByEmail("new@smartlib.test")).thenReturn(Optional.of(User.builder().build()));

        assertThatThrownBy(() -> authService.register(register("STUDENT")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void login_success_returnsToken() {
        User user = User.builder().id("u1").name("New Student")
                .email("new@smartlib.test").role(Role.STUDENT)
                .passwordHash(new BCryptPasswordEncoder().encode("Password123"))
                .active(true).build();
        when(userRepository.findByEmail("new@smartlib.test")).thenReturn(Optional.of(user));

        LoginDto dto = new LoginDto();
        dto.setEmail("new@smartlib.test");
        dto.setPassword("Password123");

        AuthResponse response = authService.login(dto);
        assertThat(response.getToken()).isNotBlank();
        assertThat(jwtService.parse(response.getToken())).isPresent();
        assertThat(jwtService.parse(response.getToken()).get().getSubject()).isEqualTo("u1");
    }

    @Test
    void login_wrongPassword_genericFailureMessage() {
        User user = User.builder().id("u1").name("New Student")
                .email("new@smartlib.test").role(Role.STUDENT)
                .passwordHash(new BCryptPasswordEncoder().encode("Password123"))
                .active(true).build();
        when(userRepository.findByEmail("new@smartlib.test")).thenReturn(Optional.of(user));

        LoginDto dto = new LoginDto();
        dto.setEmail("new@smartlib.test");
        dto.setPassword("WrongPass1");

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Invalid email or password.");
    }

    @Test
    void login_unknownEmail_sameGenericFailureMessage() {
        when(userRepository.findByEmail("ghost@smartlib.test")).thenReturn(Optional.empty());

        LoginDto dto = new LoginDto();
        dto.setEmail("ghost@smartlib.test");
        dto.setPassword("Whatev3rpass");

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Invalid email or password.");
    }

    @Test
    void tamperedToken_rejected() {
        when(userRepository.findByEmail("new@smartlib.test")).thenReturn(Optional.empty());
        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        // generate a token from a real user then tamper with it
        User user = User.builder().id("u9").name("T").email("t@t.test").role(Role.STUDENT).build();
        String token = jwtService.generateToken(user);

        assertThat(jwtService.parse(token)).isPresent();
        assertThat(jwtService.parse(token + "x")).isEmpty();
        assertThat(jwtService.parse(token.replace(".", "-"))).isEmpty();
    }
}
