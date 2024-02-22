/*
 * Copyright (c) 2022-2032 NOMIKY
 * 不能修改和删除上面的版权声明
 * 此代码属于NOMIKY编写，在未经允许的情况下不得传播复制
 */
package com.nomiky.dockerlogger.bean;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.nomiky.dockerlogger.SessionContext;
import lombok.RequiredArgsConstructor;
import org.nomiky.nomikyframework.constant.DaoConstants;
import org.nomiky.nomikyframework.entity.Page;
import org.nomiky.nomikyframework.exception.ServiceException;
import org.nomiky.nomikyframework.executor.DaoExecutor;
import org.nomiky.nomikyframework.executor.DaoExecutorManager;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nomiky
 * @since 2024年01月24日 11时23分
 */
@Component("sessionBean")
@RequiredArgsConstructor
public class SessionBean {

    private final DaoExecutorManager executorManager;

    private final JdbcTemplate jdbcTemplate;

    public Map<String, Object> getLoginUser(Map<String, Object> params) {
        Map<String, Object> userMap = SessionContext.getSession((String) params.get("token"));
        if (MapUtil.isEmpty(userMap)) {
            throw new ServiceException("用户不存在！");
        }

        return userMap;
    }

    public Map<String, Object> login(Map<String, Object> params) {
        DaoExecutor userExecutor = executorManager.getExecutor("LICENSE_BOX.user");
        Map<String, Object> userMap = userExecutor.selectOne(params);
        if (MapUtil.isEmpty(userMap)) {
            throw new ServiceException("登录失败！");
        }

        String token = IdUtil.nanoId();
        userMap.put("token", token);
        SessionContext.addSession(token, userMap);
        userMap.remove("password");


        Map<String, Object> updateMap = new HashMap<>(2);
        updateMap.put("id", userMap.get("id"));
        updateMap.put("updateTime", DateUtil.date());
        updateMap.put("updateUserId", userMap.get("id"));
        updateMap.put("updateUserName", userMap.get("name"));
        userExecutor.updateById(updateMap);

        return userMap;
    }

    public Page getMyContainers(Map<String, Object> justParams) {
        long current = Long.parseLong(justParams.get(DaoConstants.PAGING_CURRENT).toString());
        long size = Long.parseLong(justParams.get(DaoConstants.PAGING_SIZE).toString());
        Page page = new Page();
        page.setCurrent(current);
        page.setSize(size);
        String sql = "select distinct c.* from container c left join r_group_container rgc on c.id = rgc.container_id";
        String whereSql = "";
        Map<String, Object> sessionUser = SessionContext.getSession();
        assert sessionUser != null;
        List<Object> params = new ArrayList<>();
        // !admin
        if (!Long.valueOf(1).equals(sessionUser.get("id"))) {
            whereSql += (params.size() > 0 ? " and " : " where") + " rgc.group_id IN (select u.groups from `user` u where u.id = ?)";
            params.add(sessionUser.get("id"));
        }

        if (justParams.containsKey("name")) {
            whereSql += (params.size() > 0 ? " and " : " where") + " c.name like concat('%', ?, '%')";
            params.add(justParams.get("name"));
        }


        if (justParams.containsKey("host")) {
            whereSql += (params.size() > 0 ? " and " : " where") + " c.host like concat('%', ?, '%')";
            params.add(justParams.get("host"));
        }

        sql += whereSql;
        String countSql = "select count(*) from (" + sql + ") t";

        Long count = jdbcTemplate.query(countSql, rs -> rs.next() ? rs.getLong(1) : 0L, params.toArray());
        page.setTotal(count);
        if (count < 1) {
            page.setRecords(new ArrayList<>(0));
            return page;
        }

        sql += " limit ?, ?";
        params.add((current - 1) * size);
        params.add(size);
        List<Map<String, Object>> containers = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> result = new HashMap<>();
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                result.put(StrUtil.toCamelCase(metaData.getColumnLabel(i)), rs.getObject(i));
            }

            return result;
        }, params.toArray());

        page.setRecords(containers);
        return page;
    }

    public boolean logout(Map<String, Object> params) {
        SessionContext.removeSession((String) params.get("token"));
        return true;
    }
}
