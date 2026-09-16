package au.com.stickybeak.product.service.impl;

import au.com.stickybeak.product.entity.Tag;
import au.com.stickybeak.product.mapper.TagMapper;
import au.com.stickybeak.product.service.TagService;
import au.com.stickybeak.product.vo.TagVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;

    public TagServiceImpl(TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    @Override
    public List<TagVO> listAll() {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Tag::getId);
        List<Tag> tags = tagMapper.selectList(wrapper);
        return tags.stream().map(this::toVO).collect(Collectors.toList());
    }

    private TagVO toVO(Tag tag) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        return vo;
    }
}
