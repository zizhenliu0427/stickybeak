package au.com.stickybeak.payment.mapper;

import au.com.stickybeak.payment.entity.ExchangeRate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExchangeRateMapper extends BaseMapper<ExchangeRate> {
}
