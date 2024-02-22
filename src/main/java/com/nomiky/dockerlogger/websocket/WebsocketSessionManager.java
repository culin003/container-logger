/*
 * Copyright (c) 2022-2032 NOMIKY
 * 不能修改和删除上面的版权声明
 * 此代码属于NOMIKY编写，在未经允许的情况下不得传播复制
 */
package com.nomiky.dockerlogger.websocket;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author nomiky
 * @since 2024年02月04日 13时24分
 */
@Slf4j
@Service
public class WebsocketSessionManager {

    /**
     * 静态变量，用来记录当前在线连接数，线程安全的类。
     */
    private static final AtomicInteger onlineSessionClientCount = new AtomicInteger(0);

    /**
     * 存放所有在线的客户端
     */
    private static final Map<String, Session> onlineSessionClientMap = new ConcurrentHashMap<>();


    public void addSession(String key, Session session) {
        if (ObjectUtil.isAllEmpty(key, session)) {
            return;
        }

        onlineSessionClientMap.put(key, session);
        onlineSessionClientCount.incrementAndGet();
    }

    public void removeSession(String key) {
        if (StrUtil.isEmpty(key)) {
            return;
        }

        Session session = onlineSessionClientMap.remove(key);
        if (null != session) {
            onlineSessionClientCount.decrementAndGet();
        }
    }

    public int getOnlineCount(){
        return onlineSessionClientCount.get();
    }

    /**
     * 群发消息
     *
     * @param message 消息
     */
    public void sendToAll(String sid, String message) {
        // 遍历在线map集合
        onlineSessionClientMap.forEach((onlineSid, toSession) -> {
            // 排除掉自己
            if (!sid.equalsIgnoreCase(onlineSid)) {
                log.info("服务端给客户端群发消息 ==> sid = {}, toSid = {}, message = {}", sid, onlineSid, message);
                toSession.getAsyncRemote().sendText(message);
            }
        });
    }

    /**
     * 指定发送消息
     *
     * @param toSid
     * @param message
     */
    public void sendToOne(String toSid, String message) {
        // 通过sid查询map中是否存在
        Session toSession = onlineSessionClientMap.get(toSid);
        if (toSession == null) {
            log.error("服务端给客户端发送消息 ==> toSid = {} 不存在, message = {}", toSid, message);
            return;
        }
        // 异步发送
        try {
            toSession.getBasicRemote().sendText(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 指定发送消息
     *
     * @param toSid
     * @param message
     */
    public void sendToOne(String toSid, InputStream message) {
        // 通过sid查询map中是否存在
        Session toSession = onlineSessionClientMap.get(toSid);
        if (toSession == null) {
            log.error("服务端给客户端发送消息 ==> toSid = {} 不存在, message = {}", toSid, message);
            return;
        }
        // 异步发送
        try {
            IoUtil.copy(message, toSession.getBasicRemote().getSendStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
