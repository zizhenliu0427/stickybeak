package au.com.stickybeak.product.service.impl;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.product.service.StorageService;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class StorageServiceImpl implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageServiceImpl.class);

    /** 允许的图片类型 */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long PRESIGN_EXPIRY_MINUTES = 10;

    private final MinioClient minioClient;
    private final MinioClient minioPresignClient;

    @Value("${minio.bucket:stickybeak}")
    private String bucket;

    @Value("${minio.public-endpoint:http://localhost:9000}")
    private String publicEndpoint;

    public StorageServiceImpl(MinioClient minioClient, MinioClient minioPresignClient) {
        this.minioClient = minioClient;
        this.minioPresignClient = minioPresignClient;
    }

    /** 启动时确保 bucket 存在且匿名可读（商品图要能被 storefront <img> 直接加载） */
    @PostConstruct
    public void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            }
            String policy = """
                    {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}
                    """.formatted(bucket);
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
        } catch (Exception e) {
            log.warn("MinIO bucket init failed (uploads will fail until MinIO is up): {}", e.getMessage());
        }
    }

    @Override
    public PresignedUpload createUploadUrl(String filename, String contentType) {
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "unsupported content type: " + contentType + " (allowed: jpeg/png/webp/gif)");
        }
        String ext = filename != null && filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT)
                : ".jpg";
        String objectKey = "products/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"))
                + "/" + UUID.randomUUID() + ext;
        try {
            String uploadUrl = minioPresignClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry((int) TimeUnit.MINUTES.toSeconds(PRESIGN_EXPIRY_MINUTES))
                            .build());
            return new PresignedUpload(objectKey, uploadUrl, publicUrl(objectKey));
        } catch (Exception e) {
            log.error("Failed to presign upload url for {}: {}", objectKey, e.getMessage());
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "failed to create upload url");
        }
    }

    @Override
    public String publicUrl(String objectKey) {
        return publicEndpoint + "/" + bucket + "/" + objectKey;
    }
}
