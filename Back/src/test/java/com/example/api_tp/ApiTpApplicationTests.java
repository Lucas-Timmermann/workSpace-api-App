package com.example.api_tp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:apptest;DB_CLOSE_DELAY=-1;MODE=MySQL",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"app.auditoria.usuario-id=1",
		"app.seed.enabled=false"
})
class ApiTpApplicationTests {

	@Test
	void contextLoads() {
	}

}
