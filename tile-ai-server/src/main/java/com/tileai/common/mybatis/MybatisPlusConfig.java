package com.tileai.common.mybatis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

/**
 * MyBatis-Plus 装配。
 *
 * <p><b>分页插件必须显式注册</b> —— 没有它,{@code selectPage(...)} <b>不会报错</b>,
 * 而是把全表查出来当"一页"返回:{@code total} 会是全量条数,{@code size} 形同虚设。
 * 数据量小时看不出问题,产品库上万条时才会暴露,且症状是"接口越来越慢"
 * 而不是"报错",极难定位。
 *
 * <p>契约校验表 T-3(分页响应含 list / total / page / size)只有在插件生效时才真正成立。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
