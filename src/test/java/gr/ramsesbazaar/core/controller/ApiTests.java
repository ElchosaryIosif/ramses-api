package gr.ramsesbazaar.core.controller;

import com.jayway.jsonpath.JsonPath;
import gr.ramsesbazaar.core.service.OrderPlaced;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ApiTests {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private PlacedOrders placed;

    private MockMvc mvc;

    /** Collects the after-commit order events, the same ones the order email is sent from. */
    @TestConfiguration
    static class PlacedOrdersConfig {
        @Bean
        PlacedOrders placedOrders() {
            return new PlacedOrders();
        }
    }

    static class PlacedOrders {
        final List<OrderPlaced> events = new CopyOnWriteArrayList<>();

        @TransactionalEventListener
        void on(OrderPlaced event) {
            events.add(event);
        }
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String login(String password) throws Exception {
        String body = mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + password + "\",\"remember\":true}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static String product(String id, String price, boolean stock, String image) {
        return "{\"id\":\"" + id + "\",\"name\":\"Item " + id + "\",\"cat\":\"jewelry\",\"price\":" + price
                + ",\"tag\":\"new\",\"desc\":\"d\",\"stock\":" + stock
                + (image == null ? "" : ",\"image\":\"" + image + "\"") + ",\"createdAt\":1700000000000}";
    }

    private void saveCatalogue(String token, int baseRev, String... products) throws Exception {
        mvc.perform(put("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseRev\":" + baseRev + ",\"products\":[" + String.join(",", products) + "]}"))
                .andExpect(status().isOk());
    }

    @Test
    void emptyCatalogueIsPublic() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rev").value(0))
                .andExpect(jsonPath("$.products", hasSize(0)));
    }

    @Test
    void seededSettingsArePublic() throws Exception {
        mvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("wrong_password"));
    }

    @Test
    void defaultPasswordSignsInAndIsFlagged() throws Exception {
        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"ramses1996\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.defaultPassword").value(true))
                .andExpect(jsonPath("$.status.secure").value(true));
    }

    @Test
    void adminEndpointsNeedAToken() throws Exception {
        mvc.perform(put("/api/products").contentType(MediaType.APPLICATION_JSON).content("{\"baseRev\":0,\"products\":[]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/session").header(HttpHeaders.AUTHORIZATION, "Bearer forged.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void savesCatalogueStoresImagesAndRejectsStaleRevisions() throws Exception {
        String token = login("ramses1996");
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG);
        saveCatalogue(token, 0, product("abcd1", "12.5", true, dataUrl));

        String body = mvc.perform(get("/api/products"))
                .andExpect(jsonPath("$.rev").value(1))
                .andExpect(jsonPath("$.products[0].price").value(12.5))
                .andExpect(jsonPath("$.products[0].image", startsWith("/api/images/")))
                .andReturn().getResponse().getContentAsString();
        String image = JsonPath.read(body, "$.products[0].image");

        mvc.perform(get(image))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(content().bytes(PNG));

        mvc.perform(put("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseRev\":0,\"products\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.rev").value(1))
                .andExpect(jsonPath("$.products", hasSize(1)));

        // Removing the product drops its now-unreferenced photo.
        saveCatalogue(token, 1);
        mvc.perform(get(image)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidProducts() throws Exception {
        String token = login("ramses1996");
        mvc.perform(put("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseRev\":0,\"products\":[" + product("x", "1", true, null) + "]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("invalid_product"));
        mvc.perform(put("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseRev\":0,\"products\":[" + product("dupe1", "1", true, null) + ","
                                + product("dupe1", "2", true, null) + "]}"))
                .andExpect(jsonPath("$.error").value("duplicate_id"));
    }

    @Test
    void ordersArePricedFromTheCatalogue() throws Exception {
        String token = login("ramses1996");
        saveCatalogue(token, 0, product("ring1", "20", true, null), product("gone1", "5", false, null));

        String order = "{\"name\":\"Μαρία\",\"phone\":\"690\",\"email\":\"m@x.gr\",\"address\":\"Καλαμάτα\","
                + "\"items\":[{\"id\":\"ring1\",\"qty\":2}]}";
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(order))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(40))
                .andExpect(jsonPath("$.shipping").value(3.5))
                .andExpect(jsonPath("$.total").value(43.5));

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(order.replace("ring1\",\"qty\":2", "gone1\",\"qty\":1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("unavailable_product"));

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(order.replace("\"name\":\"Μαρία\"", "\"name\":\"\"")))
                .andExpect(status().isUnprocessableContent());

        // Only the accepted order is announced, with catalogue prices.
        assertThat(placed.events).singleElement().satisfies(o -> {
            assertThat(o.name()).isEqualTo("Μαρία");
            assertThat(o.items()).singleElement().satisfies(l -> assertThat(l.qty()).isEqualTo(2));
            assertThat(o.total()).isEqualByComparingTo("43.50");
        });

        String list = mvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].items[0].name").value("Item ring1"))
                .andExpect(jsonPath("$[0].status").value("NEW"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(list, "$[0].id");

        mvc.perform(patch("/api/orders/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    void honeypotOrdersAreNotStored() throws Exception {
        String token = login("ramses1996");
        saveCatalogue(token, 0, product("ring1", "20", true, null));
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"b\",\"phone\":\"1\",\"email\":\"b@b\",\"address\":\"a\","
                                + "\"items\":[{\"id\":\"ring1\",\"qty\":1}],\"website\":\"spam\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$", hasSize(0)));
        assertThat(placed.events).isEmpty();
    }

    @Test
    void changingThePasswordSignsOutOldSessions() throws Exception {
        String old = login("ramses1996");
        mvc.perform(post("/api/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + old)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"current\":\"ramses1996\",\"next\":\"short\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("weak_password"));

        String body = mvc.perform(post("/api/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + old)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current\":\"ramses1996\",\"next\":\"kalamata-2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.defaultPassword").value(false))
                .andReturn().getResponse().getContentAsString();
        String fresh = JsonPath.read(body, "$.token");

        mvc.perform(get("/api/session").header(HttpHeaders.AUTHORIZATION, "Bearer " + old))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/session").header(HttpHeaders.AUTHORIZATION, "Bearer " + fresh))
                .andExpect(status().isOk());
        login("kalamata-2026");
    }

    @Test
    void settingsUpdateOnlyTheGivenFields() throws Exception {
        String token = login("ramses1996");
        mvc.perform(put("/api/settings").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"settings\":{\"iban\":\" GR16 0110 \"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban").value("GR16 0110"))
                .andExpect(jsonPath("$.bank").doesNotExist());
    }

    @Test
    void corsAllowsTheShopAndNobodyElse() throws Exception {
        mvc.perform(options("/api/products").header(HttpHeaders.ORIGIN, "https://ramsesbazaar.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://ramsesbazaar.com"));
        mvc.perform(options("/api/products").header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT"))
                .andExpect(status().isForbidden());
    }
}
