package au.com.stickybeak.product.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;
import au.com.stickybeak.product.entity.Category;
import au.com.stickybeak.product.mapper.CategoryMapper;
import au.com.stickybeak.product.service.impl.CategoryServiceImpl;
import au.com.stickybeak.product.vo.CategoryVO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    @Test
    void listAll_returnsCategoriesOrdered() {
        Category c1 = new Category();
        c1.setId(1L);
        c1.setName("大学公交路牌");
        c1.setSlug("bus-sign");
        c1.setSortOrder(1);

        Category c2 = new Category();
        c2.setId(2L);
        c2.setName("超市系列");
        c2.setSlug("supermarket");
        c2.setSortOrder(2);

        when(categoryMapper.selectList(any())).thenReturn(List.of(c1, c2));

        List<CategoryVO> result = categoryService.listAll();
        assertEquals(2, result.size());
        assertEquals("bus-sign", result.get(0).getSlug());
        assertEquals("supermarket", result.get(1).getSlug());
    }

    @Test
    void getBySlug_found() {
        Category c = new Category();
        c.setId(1L);
        c.setName("大学公交路牌");
        c.setSlug("bus-sign");
        c.setSortOrder(1);

        when(categoryMapper.selectOne(any())).thenReturn(c);

        CategoryVO result = categoryService.getBySlug("bus-sign");
        assertNotNull(result);
        assertEquals("bus-sign", result.getSlug());
        assertEquals("大学公交路牌", result.getName());
    }

    @Test
    void getBySlug_notFound_throwsException() {
        when(categoryMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> categoryService.getBySlug("unknown"));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }
}
