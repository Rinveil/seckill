package com.seckill.activity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_activity")
public class Activity {

    public static final int STATUS_CLOSED = 0;
    public static final int STATUS_OPEN = 1;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    @TableField("price_fen")
    private Integer priceFen;
    @TableField("origin_price_fen")
    private Integer originPriceFen;
    /** 配置库存（预热时写入 Redis 的源值） */
    private Integer stock;
    /** 0=关 1=开 */
    private Integer status;
    @TableField("start_at")
    private LocalDateTime startAt;
    @TableField("end_at")
    private LocalDateTime endAt;
}
