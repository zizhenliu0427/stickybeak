package au.com.stickybeak.product.service.impl;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.product.entity.Category;
import au.com.stickybeak.product.mapper.CategoryMapper;
import au.com.stickybeak.product.service.CategoryService;
import au.com.stickybeak.product.vo.CategoryVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;

    public CategoryServiceImpl(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @Override
    public List<CategoryVO> listAll() {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Category::getSortOrder);
        
        List<Category> categories = categoryMapper.selectList(wrapper);
        return categories.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public CategoryVO getBySlug(String slug) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Category::getSlug, slug);
        Category category = categoryMapper.selectOne(wrapper);
        
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Category not found");
        }
        return toVO(category);
    }

    private CategoryVO toVO(Category category) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setSlug(category.getSlug());
        vo.setSortOrder(category.getSortOrder());
        return vo;
    }
}
