package com.yisoft.development.ecommerce.shared.persistence;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DatabaseInfrastructureTests {

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private ConnectionFactory connectionFactory;


    @Test
    void connectionFactoryUsesPool(){
        assertThat(connectionFactory).isInstanceOf(ConnectionPool.class);
    }

    @Test
    void databaseClientExecutesQuery(){
        Mono<Long> query = databaseClient.sql("""
        SELECT 1
        """)
                .map((row, metadata) -> Objects.requireNonNull(row.get(0, Long.class)))
                .one();

        StepVerifier.create(query)
                .expectNext(1L)
                .verifyComplete();
    }

    @Test
    void firstMigrationIsApplied(){
        Mono<Long> query = databaseClient.sql("""
        SELECT COUNT(*)
        FROM flyway_schema_history
        WHERE version = '1' AND success = true
        """)
                .map((row, metadata) -> Objects.requireNonNull(row.get(0, Long.class)))
                .one();

        StepVerifier.create(query)
                .expectNext(1L)
                .verifyComplete();
    }

    @Test
    void setUpdatedAtFunctionExists(){
        Mono<String> query = databaseClient.sql("""
        SELECT pg_proc.proname FROM pg_proc WHERE proname = 'set_updated_at';
        """)
                .map((row, metadata) -> Objects.requireNonNull(row.get(0, String.class)))
                .one();

        StepVerifier.create(query)
                .expectNext("set_updated_at")
                .verifyComplete();
    }
}
