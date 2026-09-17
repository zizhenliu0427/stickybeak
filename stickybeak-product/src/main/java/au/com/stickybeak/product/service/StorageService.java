package au.com.stickybeak.product.service;

/**
 * 对象存储抽象（开发 MinIO ↔ 生产 S3/OSS 只换配置）。
 */
public interface StorageService {

    /** 预签名 PUT 上传 URL（浏览器直传，不经过服务端带宽） */
    PresignedUpload createUploadUrl(String filename, String contentType);

    /** 对象公网可读 URL */
    String publicUrl(String objectKey);

    record PresignedUpload(String objectKey, String uploadUrl, String publicUrl) {
    }
}
