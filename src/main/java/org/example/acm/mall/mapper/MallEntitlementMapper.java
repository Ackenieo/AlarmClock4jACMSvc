package org.example.acm.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.example.acm.mall.entity.MallEntitlement;

import java.time.LocalDateTime;

public interface MallEntitlementMapper extends BaseMapper<MallEntitlement> {

    /**
     * 靠 UNIQUE(user_id, product_id) 做 upsert：重复发货退化成一次 no-op 更新，
     * 这是防重复解锁的第三层保护（前两层是条件 UPDATE 与 uk_pay_trade_no）。
     */
    @Insert("""
            INSERT INTO mall_entitlement
                (user_id, product_id, category, order_id, out_trade_no, status,
                 granted_time, revoked_time, create_time, update_time, deleted)
            VALUES
                (#{userId}, #{productId}, #{category}, #{orderId}, #{outTradeNo}, 'ACTIVE',
                 #{grantedTime}, NULL, #{grantedTime}, #{grantedTime}, 0)
            ON DUPLICATE KEY UPDATE
                status       = 'ACTIVE',
                revoked_time = NULL,
                order_id     = #{orderId},
                out_trade_no = #{outTradeNo},
                granted_time = #{grantedTime},
                update_time  = #{grantedTime},
                deleted      = 0
            """)
    int upsertActive(MallEntitlement entitlement);

    /** 只回收由本单发放且仍有效的权益 */
    @Update("""
            UPDATE mall_entitlement
            SET status = 'REVOKED', revoked_time = #{time}, update_time = #{time}
            WHERE order_id = #{orderId} AND status = 'ACTIVE' AND deleted = 0
            """)
    int revokeByOrderId(@Param("orderId") Long orderId, @Param("time") LocalDateTime time);
}
