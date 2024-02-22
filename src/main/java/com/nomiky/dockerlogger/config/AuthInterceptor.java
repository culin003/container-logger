/*
 * Copyright (c) 2022-2032 NOMIKY
 * 不能修改和删除上面的版权声明
 * 此代码属于NOMIKY编写，在未经允许的情况下不得传播复制
 */
package com.nomiky.dockerlogger.config;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import com.nomiky.dockerlogger.SessionContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.nomiky.nomikyframework.entity.R;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;

/**
 * TODO:
 *
 * @author nomiky
 * @since 2024年02月01日 16时18分
 */
public class AuthInterceptor implements HandlerInterceptor, Ordered {
    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String url = request.getRequestURI();
        if ("/session/info".equals(url) && HttpMethod.POST.name().equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader("auth-token");
        if (CharSequenceUtil.isEmpty(token)) {
            return errorResponse(response, R.fail(401, "用户未登录！"));
        }

        Object session = SessionContext.getSession(token);
        if (null == session) {
            return errorResponse(response, R.fail(401, "会话已超时，请重新登录！"));
        }

        return HandlerInterceptor.super.preHandle(request, response, handler);
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }

    private boolean errorResponse(HttpServletResponse response, R<?> r) throws IOException {
        String returnStr = JSONUtil.toJsonStr(r);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write(returnStr);
        return Boolean.FALSE;
    }

}
