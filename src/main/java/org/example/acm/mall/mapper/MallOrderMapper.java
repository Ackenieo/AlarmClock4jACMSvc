package org.example.acm.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.acm.mall.entity.MallOrder;

import java.time.LocalDateTime;
import java.util.List;

public interface MallOrderMapper extends BaseMapper<MallOrder> {

    /**
     * 条件更新是幂等状态机的原语：WHERE 里带上前置状态，并发调用只有一个能返回 1。
     * 支付宝异步通知与主动查单可能同时到达，靠这一条语句保证只发一次货。
     * 手写 SQL 不会被 @TableLogic 自动追加 deleted = 0，必须自己写。
     */
    @Update("""
            UPDATE mall_order
            SET status        = #{to},
                paid_time     = IF(#{to} = 'PAID', #{time}, paid_time),
                closed_time   = IF(#{to} = 'CLOSED', #{time}, closed_time),
                refunded_time = IF(#{to} = 'REFUNDED', #{time}, refunded_time),
                update_time   = #{time}
            WHERE id = #{id} AND status = #{from} AND deleted = 0
            """)
    int transitStatus(@Param("id") Long id,
                      @Param("from") String from,
                      @Param("to") String to,
                      @Param("time") LocalDateTime time);

    @Select("""
            SELECT * FROM mall_order
            WHERE status = 'WAITING_PAY' AND expire_time < #{now} AND deleted = 0
            ORDER BY id LIMIT #{limit}
            """)
    List<MallOrder> selectExpiredWaitingPay(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
