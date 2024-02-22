/*
 * Copyright (c) 2022-2032 NOMIKY
 * 不能修改和删除上面的版权声明
 * 此代码属于NOMIKY编写，在未经允许的情况下不得传播复制
 */
package com.nomiky.dockerlogger.config;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.map.MapUtil;
import com.nomiky.dockerlogger.SessionContext;
import org.nomiky.nomikyframework.bean.FrameworkBeanProcessor;
import org.nomiky.nomikyframework.entity.FrameworkConfig;
import org.nomiky.nomikyframework.executor.FieldValueAutoGenaratorHelper;
import org.nomiky.nomikyframework.executor.FieldValueAutoGenerator;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nomiky
 * @since 2024年01月23日 16时13分
 */
@Configuration
public class AutoConfiguration implements WebMvcConfigurer {

    @Bean
    public static DataSource dataSource(Environment environment) {
        DataSourceProperties properties = Binder.get(environment).bind("spring.datasource", DataSourceProperties.class).get();
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    public static JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public DataSourceTransactionManager dataSourceTransactionManager(DataSource dataSource) {
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager();
        transactionManager.setDataSource(dataSource);
        return transactionManager;
    }

    @Bean
    public FrameworkConfig frameworkConfig() {
        FrameworkConfig frameworkConfig = new FrameworkConfig() {
            @Override
            public Map<String, FieldValueAutoGenerator> createValueAutoGenerator() {
                Map<String, FieldValueAutoGenerator> valueAutoGeneratorMap = new HashMap<>();
                valueAutoGeneratorMap.put(FieldValueAutoGenaratorHelper.INSERT, valueMap -> {
                    Map<String, Object> userSession = SessionContext.getSession();
                    if (MapUtil.isNotEmpty(userSession)) {
                        valueMap.put("createUserId", userSession.get("id"));
                        valueMap.put("createUserName", userSession.get("name"));
                    }
                });
                valueAutoGeneratorMap.put(FieldValueAutoGenaratorHelper.UPDATE, valueMap -> {
                    Map<String, Object> userSession = SessionContext.getSession();
                    if (MapUtil.isNotEmpty(userSession)) {
                        valueMap.put("updateUserId", userSession.get("id"));
                        valueMap.put("updateUserName", userSession.get("name"));
                        valueMap.put("updateTime", DateUtil.date());
                    }
                });
                return valueAutoGeneratorMap;
            }
        };

        frameworkConfig.setPrintSql(true);
        frameworkConfig.setUseLogicDelete(true);
        return frameworkConfig;
    }

    @Bean
    public static FrameworkBeanProcessor frameworkBeanProcessor(JdbcTemplate jdbcTemplate, FrameworkConfig frameworkConfig) {
        return new FrameworkBeanProcessor(jdbcTemplate, frameworkConfig);
    }


    @Bean
    public AuthInterceptor authGlobalInterceptor() {
        return new AuthInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authGlobalInterceptor()).addPathPatterns("/**");
    }

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*")
                .allowCredentials(false);
    }

}
