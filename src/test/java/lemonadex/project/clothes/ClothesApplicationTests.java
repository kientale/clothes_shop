package lemonadex.project.clothes;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ClothesApplicationTests extends PostgresTestSupport {

    @Autowired JdbcTemplate jdbc;

	@Test
	void contextLoads() {

        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForList("select code from roles where deleted = false order by code", String.class))
                .containsExactly("ADMIN", "CUSTOMER");
	}

}
