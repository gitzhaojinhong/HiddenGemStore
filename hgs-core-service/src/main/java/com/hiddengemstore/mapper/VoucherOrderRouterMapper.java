package com.hiddengemstore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hiddengemstore.entity.VoucherOrderRouter;
import com.hiddengemstore.kafka.message.SeckillVoucherMessage;
import com.hiddengemstore.message.MessageExtend;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VoucherOrderRouterMapper extends BaseMapper<VoucherOrderRouter> {
}
