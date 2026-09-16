package au.com.stickybeak.product.service;

import au.com.stickybeak.product.vo.CategoryVO;
import java.util.List;

public interface CategoryService {
    List<CategoryVO> listAll();
    CategoryVO getBySlug(String slug);
}
