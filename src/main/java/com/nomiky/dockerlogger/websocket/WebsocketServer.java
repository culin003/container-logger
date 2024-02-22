/*
 * Copyright (c) 2022-2032 NOMIKY
 * 不能修改和删除上面的版权声明
 * 此代码属于NOMIKY编写，在未经允许的情况下不得传播复制
 */
package com.nomiky.dockerlogger.websocket;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.jcraft.jsch.JSchException;
import com.nomiky.dockerlogger.bean.ContainerLoggerBean;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 *
 * @author nomiky
 * @since 2024年02月02日 17时19分
 */
@Slf4j
@Component
@ServerEndpoint("/{sid}/ws")
public class WebsocketServer {

    private static WebsocketSessionManager sessionManager;

    @Lazy
    @Autowired
    public void setSessionManager(WebsocketSessionManager sessionManager) {
        WebsocketServer.sessionManager = sessionManager;
    }

    private static ContainerLoggerBean loggerBean;

    @Lazy
    @Autowired
    public void setLoggerBean(ContainerLoggerBean loggerBean) {
        WebsocketServer.loggerBean = loggerBean;
    }

    public WebsocketServer(){}

    /**
     * 连接建立成功调用的方法。由前端<code>new WebSocket</code>触发
     *
     * @param sid     每次页面建立连接时传入到服务端的id，比如用户id等。可以自定义。
     * @param session 与某个客户端的连接会话，需要通过它来给客户端发送消息
     */
    @OnOpen
    public void onOpen(@PathParam("sid") String sid, @PathParam("type") String type, Session session) {
        // 验证参数
        log.info("连接建立中 ==> session_id = {}， sid = {}", session.getId(), sid);
        sessionManager.addSession(sid, session);
        sessionManager.sendToOne(sid, "连接成功\r\n");
        log.info("连接建立成功，当前在线数为：{} ==> 开始监听新连接：session_id = {}， sid = {},。", sessionManager.getOnlineCount(), session.getId(), sid);
        // 发起ssh请求，异步发送消息
        try {
            boolean created = loggerBean.createContainerLoggerStream("","", sid);
            if (created){
                sessionManager.sendToOne(sid, "开始接收日志信息...\r\n");
            }else{
                sessionManager.sendToOne(sid, "日志信息流获取失败！");
                session.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, ""));
            }
        } catch (JSchException | IOException e) {
            sessionManager.sendToOne(sid, "日志信息流获取失败！");
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, ""));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * 连接关闭调用的方法。由前端<code>socket.close()</code>触发
     *
     * @param sid
     * @param session
     */
    @OnClose
    public void onClose(@PathParam("sid") String sid, Session session) {
        sessionManager.removeSession(sid);
        loggerBean.stopStream(sid);
        log.info("连接关闭成功，当前在线数为：{} ==> 关闭该连接信息：session_id = {}， sid = {},。", sessionManager.getOnlineCount(), session.getId(), sid);
    }

    /**
     * 收到客户端消息后调用的方法。由前端<code>socket.send</code>触发
     * * 当服务端执行toSession.getAsyncRemote().sendText(xxx)后，前端的socket.onmessage得到监听。
     *
     * @param message
     */
    @OnMessage
    public void onMessage(@PathParam("sid") String sid, String message) {
        JSONObject jsonObject = JSONUtil.parseObj(message);
        String toSid = jsonObject.getStr("sid");
        String msg = jsonObject.getStr("message");
        log.info("服务端收到客户端消息 ==> fromSid = {}, toSid = {}, message = {}", sid, toSid, message);

        if (StrUtil.isEmpty(toSid)) {
            sessionManager.sendToAll(sid, msg);
        } else {
            sessionManager.sendToOne(toSid, msg);
        }
    }

    /**
     * 发生错误调用的方法
     *
     * @param session
     * @param error
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket发生错误，错误信息为：" + error.getMessage());
        error.printStackTrace();
    }

}
