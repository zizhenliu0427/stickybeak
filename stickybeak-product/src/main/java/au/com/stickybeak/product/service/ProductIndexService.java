package au.com.stickybeak.product.service;

/**
 * ES 索引维护：单条增删 + 全量重建。
 */
public interface ProductIndexService {

    /** 从 DB 读最新数据 upsert 到 ES（商品变更事件驱动） */
    void upsertFromDb(Long productId);

    void delete(Long productId);

    /** 全量重建索引（管理端手动触发 / 首次导入后），返回索引条数 */
    int reindexAll();
}
