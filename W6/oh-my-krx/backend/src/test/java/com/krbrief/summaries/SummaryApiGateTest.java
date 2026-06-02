package com.krbrief.summaries;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"app.publicKey=secret"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SummaryApiGateTest {
  @Container
  static final MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.0")
          .withDatabaseName("testdb")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", mysql::getJdbcUrl);
    r.add("spring.datasource.username", mysql::getUsername);
    r.add("spring.datasource.password", mysql::getPassword);
  }

  @Autowired MockMvc mvc;

  @Test
  void blocks_without_key() throws Exception {
    mvc.perform(get("/api/summaries/2026-02-15")).andExpect(status().isUnauthorized());
  }

  @Test
  void allows_with_key() throws Exception {
    mvc.perform(get("/api/summaries/2026-02-15?k=secret")).andExpect(status().isNotFound());
  }
}
