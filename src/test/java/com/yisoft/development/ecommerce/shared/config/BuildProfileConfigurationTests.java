package com.yisoft.development.ecommerce.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <table>
 *     <caption>Pruebas Config Profiles</caption>
 *     <tr><th>Prueba</th><th>Qué comprueba</th></tr>
 *     <tr><td>mavenPlaceholdersAreResolved</td><td>logging.level.root, logging.level.com.yisoft.development.ecommerce y spring.r2dbc.url tienen valor y no contienen @</td></tr>
 *     <tr><td>springProfilesAreNotUsed</td><td>environment.getActiveProfiles() está vacío</td></tr>
 * </table>
 */
@SpringBootTest
class BuildProfileConfigurationTests {

    @Autowired
    private Environment environment;

    @Test
    void mavenPlaceholdersAreResolvedLoggingRoot(){
        assertThat(environment.getProperty("logging.level.root"))
                .isNotEmpty()
                .doesNotContain("@");
    }

    @Test
    void mavenPlaceholdersAreResolvedLoggingLevel(){
        assertThat(environment.getProperty("logging.level.com.yisoft.development.ecommerce"))
                .isNotEmpty()
                .doesNotContain("@");
    }

    @Test
    void mavenPlaceholdersAreResolvedLoggingR2dbc(){
        assertThat(environment.getProperty("spring.r2dbc.url"))
                .isNotEmpty()
                .doesNotContain("@");
    }

    @Test
    void springProfilesAreNotUsed(){
        assertThat(environment.getActiveProfiles()).isEmpty();
    }
}
