package au.com.stickybeak.product.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 对象存储。两个 client：
 * - internal：容器网络内直连（sb-minio:9000），做 bucket 初始化等管理操作
 * - presign：用浏览器可达的公网 endpoint（localhost:9000）签发预签名 URL——
 *   SigV4 签名与 Host 绑定，预签名 URL 的 host 必须是浏览器实际访问的 host
 */
@Configuration
public class MinioConfig {

    @Value("${minio.endpoint:http://localhost:9000}")
    private String internalEndpoint;

    @Value("${minio.public-endpoint:http://localhost:9000}")
    private String publicEndpoint;

    @Value("${minio.access-key:minioadmin}")
    private String accessKey;

    @Value("${minio.secret-key:minioadmin}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(internalEndpoint)
                .credentials(accessKey, secretKey)
                .region("us-east-1")
                .build();
    }

    @Bean
    public MinioClient minioPresignClient() {
        return MinioClient.builder()
                .endpoint(publicEndpoint)
                .credentials(accessKey, secretKey)
                // 关键：显式 region，否则 SDK 预签名前会联网查 bucket region（public endpoint 在容器内不可达）
                .region("us-east-1")
                .build();
    }
}
