package com.jarvis.commerce.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserAddressControllerTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserAddressRepository addressRepository;

    private Long userId;

    @BeforeEach
    void createUser() {
        userId = userRepository.save(new User("address-user-" + System.nanoTime(),
                "address-" + System.nanoTime() + "@example.com", null)).getId();
    }

    @Test
    void makesFirstAddressDefaultAutomatically() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/addresses", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("家", "学习路1号", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.defaultAddress").value(true));
    }

    @Test
    void switchesDefaultAddressAndListsItFirst() throws Exception {
        UserAddress first = addressRepository.save(new UserAddress(userId, request("家", "1号", true), true));
        UserAddress second = addressRepository.save(new UserAddress(userId, request("公司", "2号", false), false));

        mockMvc.perform(post("/api/users/{userId}/addresses/{addressId}/default", userId, second.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultAddress").value(true));

        mockMvc.perform(get("/api/users/{userId}/addresses", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(second.getId()))
                .andExpect(jsonPath("$[1].id").value(first.getId()));
    }

    @Test
    void preventsCrossUserAccessAndDisabledUserChanges() throws Exception {
        UserAddress address = addressRepository.save(new UserAddress(userId, request("家", "1号", true), true));
        User other = userRepository.save(new User("another-" + System.nanoTime(),
                "another-" + System.nanoTime() + "@example.com", null));

        mockMvc.perform(get("/api/users/{userId}/addresses/{addressId}", other.getId(), address.getId()))
                .andExpect(status().isNotFound());

        User owner = userRepository.findById(userId).orElseThrow();
        owner.disable();
        userRepository.flush();
        mockMvc.perform(delete("/api/users/{userId}/addresses/{addressId}", userId, address.getId()))
                .andExpect(status().isConflict());
    }

    private String addressJson(String label, String detail, boolean isDefault) {
        return "{\"label\":\"" + label + "\",\"receiverName\":\"Jarvis\",\"phone\":\"13800138000\","
                + "\"province\":\"北京\",\"city\":\"北京市\",\"district\":\"海淀区\","
                + "\"detailAddress\":\"" + detail + "\",\"defaultAddress\":" + isDefault + "}";
    }

    private AddressRequest request(String label, String detail, boolean isDefault) {
        return new AddressRequest(label, "Jarvis", "13800138000", "北京", "北京市", "海淀区",
                detail, null, isDefault);
    }
}
