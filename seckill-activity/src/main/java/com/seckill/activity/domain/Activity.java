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

    /** 草稿：可改配置，未预热 */
    public static final int STATUS_DRAFT = 0;
    /** 开抢中 */
    public static final int STATUS_OPEN = 1;
    /** 已预热：Redis 有库存，可开抢；允许改 Redis 库存 */
    public static final int STATUS_PREHEATED = 2;
    /** 终态：关闭后不可再开，须新建活动 */
    public static final int STATUS_CLOSED = 3;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    @TableField("price_fen")
    private Integer priceFen;
    @TableField("origin_price_fen")
    private Integer originPriceFen;
    /** 配置库存（预热时写入 Redis 的源值） */
    private Integer stock;
    /** 0=DRAFT 1=OPEN 2=PREHEATED 3=CLOSED */
    private Integer status;
    @TableField("start_at")
    private LocalDateTime startAt;
    @TableField("end_at")
    private LocalDateTime endAt;
    @TableField("limit_per_user")
    private Integer limitPerUser;
}
