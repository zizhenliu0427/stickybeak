package au.com.stickybeak.product.service;

import au.com.stickybeak.product.dto.ProductQuery;
import au.com.stickybeak.product.vo.ProductListVO;
import au.com.stickybeak.product.vo.ProductVO;

import java.util.List;

public interface ProductService {
    ProductListVO listProducts(ProductQuery query);
    ProductVO getBySlug(String slug);
    ProductVO getById(Long id);
    List<ProductVO> listByIds(List<Long> ids);
    List<ProductVO> listFeatured();
    List<ProductVO> listRelated(String categorySlug, String excludeSlug, int limit);
}
