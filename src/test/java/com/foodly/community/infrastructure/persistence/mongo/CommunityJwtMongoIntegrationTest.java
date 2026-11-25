package com.foodly.community.infrastructure.persistence.mongo;

import com.foodly.community.CommunityServiceApplication;
import com.foodly.community.application.commandhandlers.CreateReviewCommandHandler;
import com.foodly.community.application.commandhandlers.AddFavoriteCommandHandler;
import com.foodly.community.application.commandhandlers.RemoveFavoriteCommandHandler;
import com.foodly.community.application.ports.CurrentExplorer;
import com.foodly.community.application.ports.ReviewIdGenerator;
import com.foodly.community.application.queryhandlers.GetReviewSummaryByHuariqueQueryHandler;
import com.foodly.community.domain.model.Favorite;
import com.foodly.community.domain.repository.IFavoriteRepository;
import com.foodly.community.domain.repository.IReviewRepository;
import com.foodly.community.infrastructure.security.SpringSecurityCurrentExplorer;
import com.foodly.community.interfaces.rest.FavoriteController;
import com.foodly.community.interfaces.rest.ReviewController;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.foodly.community.infrastructure.security.JwtTestTokens.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Seguridad criptográfica real + MVC/handlers/repositorios reales; productor y MongoDB son temporales. */
class CommunityJwtMongoIntegrationTest {

    @TempDir static Path directory;
    private static TemporaryMongoServer server;
    private static AnnotationConfigWebApplicationContext context;
    private static MockMvc mvc;
    private static MongoTemplate mongo;
    private static final String REVIEWS = "/community/huariques/huarique-1/reviews";
    private static final String FAVORITE = "/community/favorites/huarique-1";

    @TestConfiguration(proxyBeanMethods = false)
    static class DeterministicAdapters {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean @Primary ReviewIdGenerator deterministicReviewIds() {
            var sequence = new AtomicLong();
            return () -> "integration-review-" + sequence.incrementAndGet();
        }
    }

    @BeforeAll
    static void start() throws Exception {
        server = TemporaryMongoServer.start(directory);
        try {
            context = createContext(true, true);
            mongo = context.getBean(MongoTemplate.class);
            mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            Files.writeString(Path.of("target/jwt-mongo-integration-runtime.txt"), server.runtime()
                    + "\nMongoDB " + mongo.getDb().runCommand(new Document("buildInfo", 1)).getString("version")
                    + "\nReal HS256 verification with ephemeral JJWT 0.13.0 test key; no IAM service invoked\n");
        } catch (Exception | Error failure) {
            stop();
            throw failure;
        }
    }

    @AfterAll
    static void stop() throws Exception {
        if (context != null) context.close();
        if (server != null) server.close();
    }

    @BeforeEach
    void clearOnlyOwnedTemporaryCollections() {
        mongo.getCollection("reviews").deleteMany(new Document());
        mongo.getCollection("favorites").deleteMany(new Document());
    }

    @Test
    void composesRealBeansWithoutRepositoryMocksOrFallbacks() {
        assertThat(context.getBean(IReviewRepository.class)).isInstanceOf(ReviewRepository.class);
        assertThat(context.getBean(IFavoriteRepository.class)).isInstanceOf(FavoriteRepository.class);
        assertThat(context.getBean(CurrentExplorer.class)).isInstanceOf(SpringSecurityCurrentExplorer.class);
        assertThat(context.getBeansOfType(ReviewController.class)).hasSize(1);
        assertThat(context.getBeansOfType(FavoriteController.class)).hasSize(1);
        assertThat(context.getBean(CreateReviewCommandHandler.class)).isNotNull();
        assertThat(context.getBean(AddFavoriteCommandHandler.class)).isNotNull();
        assertThat(context.getBean(RemoveFavoriteCommandHandler.class)).isNotNull();
        assertThat(context.getBean(GetReviewSummaryByHuariqueQueryHandler.class)).isNotNull();
    }

    @Test
    void signedIamShapedTokenCreatesAndQueriesAReviewInRealMongo() throws Exception {
        var response = mvc.perform(post(REVIEWS).header("Authorization", bearer(EXPLORER, "GASTRONOMIC_EXPLORER"))
                        .header("X-Explorer-Id", OTHER).param("explorerId", OTHER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"comment\":\"Buen sabor\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.explorerId").value(EXPLORER))
                .andExpect(jsonPath("$.createdAt").value(NOW.toString())).andReturn();
        assertThat(response.getResponse().getContentAsString()).contains("integration-review-");
        assertThat(mongo.getCollection("reviews").find().first().getString("explorerId")).isEqualTo(EXPLORER);
        mvc.perform(get(REVIEWS).header("Authorization", bearer(EXPLORER, "GASTRONOMIC_EXPLORER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].rating").value(5));
    }

    @Test
    void favoriteLifecycleCannotSelectAnotherExplorersIdentity() throws Exception {
        context.getBean(IFavoriteRepository.class).save(new Favorite(OTHER, "other-huarique"));
        mvc.perform(post(FAVORITE).header("Authorization", bearer(EXPLORER, "GASTRONOMIC_EXPLORER"))
                        .header("X-Explorer-Id", OTHER).param("explorerId", OTHER))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.explorerId").value(EXPLORER));
        mvc.perform(get("/community/favorites/me").header("Authorization", bearer(EXPLORER, "GASTRONOMIC_EXPLORER"))
                        .param("explorerId", OTHER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].explorerId").value(EXPLORER));
        mvc.perform(delete(FAVORITE).header("Authorization", bearer(OTHER, "GASTRONOMIC_EXPLORER")))
                .andExpect(status().isNotFound());
        mvc.perform(delete(FAVORITE).header("Authorization", bearer(EXPLORER, "GASTRONOMIC_EXPLORER")))
                .andExpect(status().isNoContent());
        mvc.perform(delete(FAVORITE).header("Authorization", bearer(EXPLORER, "GASTRONOMIC_EXPLORER")))
                .andExpect(status().isNotFound());
        assertThat(context.getBean(IFavoriteRepository.class).findByExplorerId(OTHER)).hasSize(1);
    }

    @Test
    void repeatedFavoriteIs409ThroughTheFullStack() throws Exception {
        mvc.perform(post(FAVORITE).header("Authorization", bearer(EXPLORER, "GASTRONOMIC_EXPLORER"))).andExpect(status().isCreated());
        mvc.perform(post(FAVORITE).header("Authorization", bearer(EXPLORER, "GASTRONOMIC_EXPLORER"))).andExpect(status().isConflict());
        assertThat(mongo.getCollection("favorites").countDocuments()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HUARIQUE_OWNER", "OWNER", "ROLE_GASTRONOMIC_EXPLORER", "unknown"})
    void aSignedTokenWithoutExplorerPermissionsIs403(String role) throws Exception {
        mvc.perform(post(FAVORITE).header("Authorization", bearer(EXPLORER, role)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertThat(mongo.getCollection("favorites").countDocuments()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = Invalid.class, names = {"WRONG_KEY", "EXPIRED", "MISSING_EXP", "MISSING_SUB", "HS384", "UNSIGNED", "MISSING_ROLE"})
    void invalidTokenIs401AndCannotReachPersistence(Invalid invalid) throws Exception {
        mvc.perform(post(FAVORITE).header("Authorization", "Bearer " + token(EXPLORER, "GASTRONOMIC_EXPLORER", invalid, false)))
                .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().json("{\"status\":401,\"code\":\"AUTHENTICATION_REQUIRED\",\"message\":\"Authentication required\"}"));
        assertThat(mongo.getCollection("favorites").countDocuments()).isZero();
    }

    @Test
    void allFiveBusinessOperationsRequireAuthentication() throws Exception {
        mvc.perform(get(REVIEWS)).andExpect(status().isUnauthorized());
        mvc.perform(post(REVIEWS).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}")).andExpect(status().isUnauthorized());
        mvc.perform(post(FAVORITE)).andExpect(status().isUnauthorized());
        mvc.perform(delete(FAVORITE)).andExpect(status().isUnauthorized());
        mvc.perform(get("/community/favorites/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void bodyCannotOverrideTheValidatedSubject() throws Exception {
        mvc.perform(post(REVIEWS).header("Authorization", bearer(EXPLORER, "GASTRONOMIC_EXPLORER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"explorerId\":\"spoofed\"}"))
                .andExpect(status().isBadRequest());
        assertThat(mongo.getCollection("reviews").countDocuments()).isZero();
    }

    @Test
    void pluralRolesOrScopesDoNotOverrideTheObservedSingularRoleContract() throws Exception {
        String token = Jwts.builder().subject(EXPLORER).claim("role", "HUARIQUE_OWNER")
                .claim("roles", List.of("GASTRONOMIC_EXPLORER")).claim("scope", "ROLE_GASTRONOMIC_EXPLORER")
                .issuedAt(Date.from(NOW.minusSeconds(10))).expiration(Date.from(NOW.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(KEY), Jwts.SIG.HS256).compact();
        mvc.perform(post(FAVORITE).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    @Test
    void authenticationDoesNotPersistIntoAnHttpSession() throws Exception {
        var result = mvc.perform(get(REVIEWS).header("Authorization", bearer(EXPLORER, "GASTRONOMIC_EXPLORER")))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        mvc.perform(get(REVIEWS)).andExpect(status().isUnauthorized());
    }

    @Test
    void aNonJwtAuthenticationWithTheSameRoleStillCannotAuthorizeCommunity() throws Exception {
        mvc.perform(get(REVIEWS).with(user("spoofed").roles("GASTRONOMIC_EXPLORER"))).andExpect(status().isForbidden());
    }

    @Test
    void mongoOnlyKeepsControllersAbsentAndOperationsBlocked() throws Exception {
        try (var partial = createContext(false, true)) {
            assertThat(partial.getBeansOfType(ReviewController.class)).isEmpty();
            assertThat(partial.getBeansOfType(CreateReviewCommandHandler.class)).isEmpty();
            var partialMvc = MockMvcBuilders.webAppContextSetup(partial).apply(springSecurity()).build();
            partialMvc.perform(get("/community/favorites/me")).andExpect(status().isForbidden());
            partialMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }
    }

    private static String bearer(String subject, String role) { return "Bearer " + token(subject, role); }

    private static AnnotationConfigWebApplicationContext createContext(boolean jwt, boolean mongodb) throws Exception {
        var application = new AnnotationConfigWebApplicationContext();
        application.setServletContext(new MockServletContext());
        application.getEnvironment().getPropertySources().addLast(new ResourcePropertySource("community-defaults", "classpath:application.properties"));
        application.getEnvironment().getPropertySources().addFirst(new MapPropertySource("isolated-integration", Map.of(
                "community.integrations.jwt.enabled", Boolean.toString(jwt), "community.persistence.mongodb.enabled", Boolean.toString(mongodb),
                "community.integrations.jwt.contract", "iam-current", "community.integrations.jwt.secret-base64", encodedKey(),
                "spring.data.mongodb.uri", server.uri(), "spring.data.mongodb.database", "foodly-community")));
        application.register(DeterministicAdapters.class, CommunityServiceApplication.class);
        try {
            application.refresh();
            return application;
        } catch (RuntimeException | Error failure) {
            application.close();
            throw failure;
        }
    }
}
