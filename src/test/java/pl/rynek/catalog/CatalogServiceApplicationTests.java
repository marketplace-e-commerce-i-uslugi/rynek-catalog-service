package pl.rynek.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfig.class)
class CatalogServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
