/*
 * Copyright (c) 2022-2032 NOMIKY
 * 不能修改和删除上面的版权声明
 * 此代码属于NOMIKY编写，在未经允许的情况下不得传播复制
 */
package com.nomiky.dockerlogger.websocket;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.TimeoutBlockingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * @author nomiky
 * @since 2024年02月06日 11时04分
 */
@Service
@RequiredArgsConstructor
public class DisruptorService implements InitializingBean, EventHandler<SendEvent> {

    private Disruptor<SendEvent> disruptor;
    private final WebsocketSessionManager sessionManager;

    @Override
    public void afterPropertiesSet() {
        disruptor = new Disruptor<>(
                SendEvent::new,
                1024,
                new CustomizableThreadFactory("disruptor-"),
                ProducerType.SINGLE,
                new TimeoutBlockingWaitStrategy(100, TimeUnit.MILLISECONDS)
        );

        disruptor.handleEventsWith(this);
        disruptor.start();
    }

    public void sendMessage(String sid, String message) {
        long sequence = disruptor.getRingBuffer().next();
        SendEvent sendEvent = disruptor.getRingBuffer().get(sequence);
        sendEvent.setSid(sid);
        sendEvent.setMessage(message);
        disruptor.getRingBuffer().publish(sequence);
    }

    @Override
    public void onEvent(SendEvent sendEvent, long l, boolean b) {
        sessionManager.sendToOne(sendEvent.getSid(), sendEvent.getMessage());
    }
}
