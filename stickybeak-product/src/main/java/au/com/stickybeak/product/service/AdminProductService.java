package au.com.stickybeak.product.service;

import au.com.stickybeak.product.dto.AdminProductPageQuery;
import au.com.stickybeak.product.dto.ProductPriceUpdateRequest;
import au.com.stickybeak.product.dto.ProductStatusUpdateRequest;
import au.com.stickybeak.product.dto.ProductStockUpdateRequest;
import au.com.stickybeak.product.vo.AdminProductVO;
import au.com.stickybeak.product.vo.StockAlertSummaryVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface AdminProductService {
    Page<AdminProductVO> pageProducts(AdminProductPageQuery query);
    void updateStatus(Long id, ProductStatusUpdateRequest req);
    void updateStock(Long id, ProductStockUpdateRequest req);
    void updatePrice(Long id, ProductPriceUpdateRequest req);
    StockAlertSummaryVO getStockAlerts(int threshold);
}
