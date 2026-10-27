package com.sssok.application.media;

import com.sssok.application.storage.ObjectsOrphanedEvent;
import com.sssok.domain.file.StorageKey;
import com.sssok.infrastructure.config.MediaUrlCacheProperties;
import com.sssok.renewal.outgoing.storage.FileStorage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@Tag("integration-h2")
@SpringJUnitConfig(MediaReadUrlCacheCommitTest.Config.class)
class MediaReadUrlCacheCommitTest {
    @Autowired MediaReadUrlProvider provider;
    @Autowired ApplicationEventPublisher publisher;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void 롤백은_캐시를_보존하고_커밋은_관련_키만_제거한다() {
        StorageKey key = new StorageKey("commit-test/" + UUID.randomUUID());
        StorageKey other = new StorageKey("unaffected/" + UUID.randomUUID());
        var first = resolve(key);
        var unaffected = resolve(other);
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> {
            publisher.publishEvent(new ObjectsOrphanedEvent(List.of(key)));
            assertThat(resolve(key)).isEqualTo(first);
            status.setRollbackOnly();
        });
        assertThat(resolve(key)).isEqualTo(first);
        transaction.executeWithoutResult(status -> {
            publisher.publishEvent(new ObjectsOrphanedEvent(List.of(key)));
            assertThat(resolve(key)).isEqualTo(first);
        });
        assertThat(resolve(key).url()).isNotEqualTo(first.url());
        assertThat(resolve(other)).isEqualTo(unaffected);
    }

    private MediaUrlSource.ResolvedUrl resolve(StorageKey key) {
        return provider.resolve(key, "inline", "image/webp", Duration.ofMinutes(30));
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean(destroyMethod = "shutdown")
        EmbeddedDatabase dataSource() {
            return new EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2).build();
        }
        @Bean PlatformTransactionManager transactions(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
        @Bean MediaReadUrlProvider provider() {
            FileStorage storage = mock(FileStorage.class);
            when(storage.presignGet(any(), anyString(), anyString(), any()))
                .thenAnswer(call -> UUID.randomUUID().toString());
            return new MediaReadUrlProvider(storage,
                new MediaUrlCacheProperties(true, 100L, Duration.ofMinutes(2)),
                new SimpleMeterRegistry());
        }
    }
}
