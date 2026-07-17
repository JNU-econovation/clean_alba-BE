package com.cleanmap.clean_alba_backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(properties = {
        "spring.sql.init.mode=never",
        "app.seed.real-data.enabled=false"
})
class RealDataSeederDisabledIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void doesNotCreateTheSeederWhenDisabled() {
        assertFalse(applicationContext.containsBean("realDataSeeder"));
    }
}
