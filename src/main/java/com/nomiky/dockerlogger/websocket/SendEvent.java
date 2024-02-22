/*
 * Copyright (c) 2022-2032 NOMIKY
 * 不能修改和删除上面的版权声明
 * 此代码属于NOMIKY编写，在未经允许的情况下不得传播复制
 */
package com.nomiky.dockerlogger.websocket;

import lombok.Data;

/**
 *
 * @author nomiky
 * @since 2024年02月06日 11时05分
 */
@Data
public class SendEvent {

    private String sid;

    private String message;

}
