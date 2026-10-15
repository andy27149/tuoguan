package com.tuoguan.backend.health;

import com.tuoguan.backend.support.IntegrationTestBase;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.sql.SQLException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 产品诊断 #61：此前 /api/health 硬编码返回 {"status":"ok"}，不探测任何依赖——这里验证
// 接口现在是真的探测，依赖不可用时如实报告 503，而不是不管依赖死活都答 ok。
class HealthControllerTest extends IntegrationTestBase {

    @SpyBean
    private javax.sql.DataSource dataSource;

    @SpyBean
    private MinioClient minioClient;

    @Test
    void healthEndpointReturnsOkWhenDatabaseAndStorageAreReachable() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"ok\",\"database\":\"ok\",\"storage\":\"ok\"}"));
    }

    @Test
    void healthEndpointReturns503WhenDatabaseIsUnreachable() throws Exception {
        doThrow(new SQLException("模拟数据库不可达")).when(dataSource).getConnection();

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("degraded"))
                .andExpect(jsonPath("$.database").value("down"));
    }

    @Test
    void healthEndpointReturns503WhenStorageIsUnreachable() throws Exception {
        doThrow(new RuntimeException("模拟 MinIO 不可达")).when(minioClient).bucketExists(any());

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("degraded"))
                .andExpect(jsonPath("$.storage").value("down"));
    }
}
