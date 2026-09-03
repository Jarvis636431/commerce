package com.jarvis.commerce.auth;

import com.jarvis.commerce.user.User;
import com.jarvis.commerce.user.UserRepository;
import com.jarvis.commerce.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "commerce.security.permit-all=false")
class AuthSecurityTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void registersAndUsesAccessToken() throws Exception {
        Tokens tokens = register("secure-user");

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("secure-user"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void rotatesRefreshTokenAndDetectsReuse() throws Exception {
        Tokens first = register("rotation-user");
        Tokens second = refresh(first.refreshToken(), 200);

        refresh(first.refreshToken(), 401);
        refresh(second.refreshToken(), 401);
    }

    @Test
    void logoutRevokesRefreshButAccessTokenRemainsShortLived() throws Exception {
        Tokens tokens = register("logout-user");

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
                .andExpect(status().isNoContent());
        refresh(tokens.refreshToken(), 401);

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void enforcesUserAndAdminRoles() throws Exception {
        Tokens userTokens = register("normal-role-user");
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + userTokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Forbidden Product\"}"))
                .andExpect(status().isForbidden());

        long unique = System.nanoTime();
        userRepository.save(new User("admin-" + unique, "admin-" + unique + "@example.com", null,
                passwordEncoder.encode("very-secure-password"), UserRole.ADMIN));
        Tokens adminTokens = login("admin-" + unique, "very-secure-password");
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminTokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Admin Product\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void returnsGenericErrorForWrongPassword() throws Exception {
        register("wrong-password-user");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"wrong-password-user\",\"password\":\"incorrect-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid account or password"));
    }

    @Test
    void derivesBusinessOwnerFromTokenAndPreventsHorizontalAccess() throws Exception {
        Tokens owner = register("address-owner");
        Tokens anotherUser = register("address-stranger");
        String addressBody = """
                {"label":"home","receiverName":"Jarvis","phone":"13800138000",
                 "province":"Shanghai","city":"Shanghai","district":"Pudong",
                 "detailAddress":"No. 1 Road","postalCode":"200000","defaultAddress":true}
                """;

        JsonNode address = body(mockMvc.perform(post("/api/me/addresses")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(addressBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long addressId = address.get("id").asLong();

        mockMvc.perform(get("/api/me/addresses/{id}", addressId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(address.get("userId").asLong()));

        mockMvc.perform(get("/api/me/addresses/{id}", addressId)
                        .header("Authorization", "Bearer " + anotherUser.accessToken()))
                .andExpect(status().isNotFound());
    }

    private Tokens register(String username) throws Exception {
        long unique = System.nanoTime();
        String body = "{\"username\":\"" + username + "\",\"email\":\"" + username + "-"
                + unique + "@example.com\",\"password\":\"very-secure-password\"}";
        JsonNode json = body(mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return tokens(json);
    }

    private Tokens login(String account, String password) throws Exception {
        JsonNode json = body(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return tokens(json);
    }

    private Tokens refresh(String refreshToken, int expectedStatus) throws Exception {
        var result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().is(expectedStatus)).andReturn();
        return expectedStatus == 200 ? tokens(body(result.getResponse().getContentAsString())) : null;
    }

    private JsonNode body(String value) { return objectMapper.readTree(value); }
    private Tokens tokens(JsonNode json) {
        return new Tokens(json.get("accessToken").asText(), json.get("refreshToken").asText());
    }
    private record Tokens(String accessToken, String refreshToken) {}
}
