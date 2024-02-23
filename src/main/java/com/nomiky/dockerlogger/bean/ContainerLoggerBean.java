/*
 * Copyright (c) 2022-2032 NOMIKY
 * 不能修改和删除上面的版权声明
 * 此代码属于NOMIKY编写，在未经允许的情况下不得传播复制
 */
package com.nomiky.dockerlogger.bean;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.ssh.JschUtil;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.nomiky.dockerlogger.websocket.DisruptorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nomiky.nomikyframework.executor.DaoExecutor;
import org.nomiky.nomikyframework.executor.DaoExecutorManager;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * @author nomiky
 * @since 2024年02月02日 17时40分
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContainerLoggerBean implements InitializingBean {

    private final ScheduledExecutorService threadPoolTaskScheduler = Executors.newScheduledThreadPool(10);

    private final ExecutorService executor = Executors.newFixedThreadPool(20);

    private final Map<String, Future<?>> scheduledFutureMap = new ConcurrentHashMap<>();

    private DaoExecutor containerExecutor;

    private final DaoExecutorManager executorManager;
    private final DisruptorService disruptorService;

    public boolean createContainerLoggerStream(String loggerType, String containerType, String id) throws JSchException {
        Map<String, Object> params = new HashMap<>(1);
        params.put("id", id);
        Map<String, Object> container = containerExecutor.selectOne(params);
        Session sshSession = JschUtil.openSession(
                StrUtil.toStringOrNull(container.get("host")),
                Integer.parseInt(StrUtil.emptyToDefault(StrUtil.toStringOrNull(container.get("port")), "0")),
                StrUtil.toStringOrNull(container.get("account")),
                StrUtil.toStringOrNull(container.get("password")),
                60000
        );

        if (null == sshSession || !sshSession.isConnected()) {
            return false;
        }

        ScheduledFuture<?> future = threadPoolTaskScheduler.schedule(() -> {
            BufferedReader reader = null;
            ChannelExec channel = null;
            try {
                String command = StrUtil.toStringOrNull(container.get("logCommand"));
                if (StrUtil.isEmpty(command)) {
                    command = "echo '" + container.get("password") + "' | sudo -S docker logs -f --tail=500 " + container.get("name");
                } else {
                    command = command.replace("${password}", (String) container.get("password"));
                    command = command.replace("${name}", (String) container.get("name"));
                    Object namespace = container.get("namespace");
                    if (!StrUtil.isEmptyIfStr(namespace)) {
                        command = command.replace("${namespace}", (String) namespace);
                    }else{
                        command = command.replace("${namespace}", "--all-namespaces");
                    }
                }
                channel = (ChannelExec) sshSession.openChannel("exec");
                channel.setCommand(command);
                channel.connect();

                ChannelExec finalChannel = channel;
                Future<?> errorFuture = executor.submit(() -> {
                    try (BufferedReader tempReader = new BufferedReader(new InputStreamReader(finalChannel.getErrStream()))) {
                        String line;
                        while ((line = tempReader.readLine()) != null) {
                            disruptorService.sendMessage(id, line);
                        }
                    }catch (InterruptedIOException ioe) {
                        log.error("客户端连接关闭！");
                    } catch (Exception e) {
                        log.error("获取日志流发生异常！", e);
                    }
                });

                scheduledFutureMap.put(id + "_SUB", errorFuture);

                reader = new BufferedReader(new InputStreamReader(channel.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    disruptorService.sendMessage(id, line);
                }
            } catch (Exception e) {
                log.error("获取日志流发生异常！", e);
            } finally {
                IoUtil.close(reader);
                if (null != channel) {
                    try {
                        channel.sendSignal("KILL");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    channel.disconnect();
                    sshSession.disconnect();
                }
            }
        }, 1, TimeUnit.SECONDS);

        scheduledFutureMap.put(id, future);
        return true;
    }

    public void stopStream(String id) {
        scheduledFutureMap.entrySet().removeIf(next -> {
            if (next.getKey().startsWith(id)) {
                next.getValue().cancel(true);
                return true;
            }

            return false;
        });
    }

    @Override
    public void afterPropertiesSet() {
        containerExecutor = executorManager.getExecutor("LICENSE_BOX.container");
    }

}
