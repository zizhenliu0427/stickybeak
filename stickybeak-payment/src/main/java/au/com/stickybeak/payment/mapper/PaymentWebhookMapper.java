package au.com.stickybeak.payment.mapper;

import au.com.stickybeak.payment.entity.PaymentWebhook;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentWebhookMapper extends BaseMapper<PaymentWebhook> {
}
