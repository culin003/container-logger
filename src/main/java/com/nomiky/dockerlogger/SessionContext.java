/*
 * Copyright (c) 2022-2032 NOMIKY
 * 不能修改和删除上面的版权声明
 * 此代码属于NOMIKY编写，在未经允许的情况下不得传播复制
 */
package com.nomiky.dockerlogger;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.nomiky.nomikyframework.exception.ServiceException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author nomiky
 * @since 2024年01月24日 11时24分
 */
public class SessionContext {

    private static final Map<String, Map<String, Object>> SESSION_MAP = new ConcurrentHashMap<>();

    public static void addSession(String token, Map<String, Object> userMap) {
        if (StrUtil.isEmpty(token) || MapUtil.isEmpty(userMap)) {
            return;
        }

        SESSION_MAP.put(token, userMap);
    }

    public static Map<String, Object> getSession() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String token = request.getHeader("auth-token");
        if (StrUtil.isEmpty(token)) {
            return null;
        }
        Map<String, Object> userSession = getSession(token);
        if (MapUtil.isEmpty(userSession)) {
            throw new ServiceException(401, "用户未登录！！");
        }

        return userSession;
    }

    public static Map<String, Object> getSession(String token) {
        return SESSION_MAP.get(token);
    }

    public static void removeSession(String token) {
        SESSION_MAP.remove(token);
    }

}
