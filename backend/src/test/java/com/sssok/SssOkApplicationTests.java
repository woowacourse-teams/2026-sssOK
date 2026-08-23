package com.sssok;

import com.sssok.support.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// PostgresContainerSupport(싱글톤 컨테이너)를 상속하므로 다른 API 인수 테스트와 컨테이너를 공유한다.
@SpringBootTest
class SssOkApplicationTests extends PostgresContainerSupport {

    @Test
    void contextLoads() {
    }
}
