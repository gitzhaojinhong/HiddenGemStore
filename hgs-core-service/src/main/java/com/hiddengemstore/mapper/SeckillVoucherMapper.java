package com.hiddengemstore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hiddengemstore.entity.SeckillVoucher;
import jakarta.validation.constraints.NotNull;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {

    @SuppressWarnings("SqlResolve")
    @Update("UPDATE tb_seckill_voucher SET stock = stock + 1,update_time = NOW() WHERE voucher_id = #{voucherId}")
    Integer rollbackStock(@Param("voucherId")Long voucherId);
}
