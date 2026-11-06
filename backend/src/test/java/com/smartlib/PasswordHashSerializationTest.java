package com.smartlib;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlib.dto.AuthResponse;
import com.smartlib.dto.UserResponse;
import com.smartlib.model.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordHashSerializationTest {

    @Test
    void userAndAuthResponsesDoNotExposePasswordHash() throws Exception {
        UserResponse user = new UserResponse();
        user.setId("u1");
        user.setName("Aarav Sharma");
        user.setEmail("aarav@smartlib.test");
        user.setRole(Role.STUDENT);
        user.setPoints(90);
        user.setPreferredSubjects(List.of("Computer Science"));

        AuthResponse auth = new AuthResponse();
        auth.setToken("token");
        auth.setUser(user);

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String json = mapper.writeValueAsString(auth);

        assertThat(json).contains("aarav@smartlib.test", "Aarav Sharma");
        assertThat(json.toLowerCase()).doesNotContain("passwordhash", "password");
        assertThatThrownBy(() -> UserResponse.class.getDeclaredField("passwordHash"))
                .isInstanceOf(NoSuchFieldException.class);
    }
}
