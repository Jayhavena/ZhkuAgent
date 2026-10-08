package com.zhku.knowledge.campusagent.archive;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLTransientConnectionException;
import java.util.concurrent.atomic.AtomicBoolean;

/** 故障仅在测试侧注入；ACK_LOSS 必须先真实 commit，不能把提交前异常当未知回执。 */
final class ArchiveJdbcFaults {
    static DataSource commitBarrier(DataSource source, AtomicBoolean armed,
            java.util.concurrent.CountDownLatch entered, java.util.concurrent.CountDownLatch release) {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[] { DataSource.class }, (proxy, method, args) -> {
                    Object result = invoke(source, method, args);
                    if (!method.getName().equals("getConnection"))
                        return result;
                    var connection = (Connection) result;
                    return Proxy.newProxyInstance(Connection.class.getClassLoader(),
                            new Class<?>[] { Connection.class }, (cp, cm, ca) -> {
                                if (cm.getName().equals("commit") && armed.compareAndSet(true, false)) {
                                    entered.countDown();
                                    if (!release.await(2, java.util.concurrent.TimeUnit.SECONDS))
                                        throw new java.sql.SQLException("SYNTHETIC_BARRIER_TIMEOUT");
                                }
                                return invoke(connection, cm, ca);
                            });
                });
    }

    static DataSource archiveReadBarrier(DataSource source, AtomicBoolean armed,
            java.util.concurrent.CountDownLatch entered, java.util.concurrent.CountDownLatch release) {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[] { DataSource.class }, (proxy, method, args) -> {
                    Object result = invoke(source, method, args);
                    if (!method.getName().equals("getConnection"))
                        return result;
                    var connection = (Connection) result;
                    return Proxy.newProxyInstance(Connection.class.getClassLoader(),
                            new Class<?>[] { Connection.class }, (cp, cm, ca) -> {
                                Object prepared = invoke(connection, cm, ca);
                                if (!cm.getName().equals("prepareStatement") || !((String) ca[0])
                                        .startsWith("SELECT * FROM campus_agent_run_archive WHERE run_id"))
                                    return prepared;
                                return Proxy.newProxyInstance(java.sql.PreparedStatement.class.getClassLoader(),
                                        new Class<?>[] { java.sql.PreparedStatement.class }, (sp, sm, sa) -> {
                                            Object rows = invoke(prepared, sm, sa);
                                            if (sm.getName().equals("executeQuery")
                                                    && armed.compareAndSet(true, false)) {
                                                entered.countDown();
                                                if (!release.await(2, java.util.concurrent.TimeUnit.SECONDS))
                                                    throw new java.sql.SQLException("SYNTHETIC_BARRIER_TIMEOUT");
                                            }
                                            return rows;
                                        });
                            });
                });
    }

    static DataSource commitFault(DataSource source, AtomicBoolean armed, boolean afterCommit) {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[] { DataSource.class }, (proxy, method, args) -> {
                    Object result = invoke(source, method, args);
                    if (method.getName().equals("getConnection")) {
                        var connection = (Connection) result;
                        return Proxy.newProxyInstance(Connection.class.getClassLoader(),
                                new Class<?>[] { Connection.class }, (cp, cm, ca) -> {
                                    if (cm.getName().equals("commit") && armed.compareAndSet(true, false)) {
                                        if (afterCommit)
                                            connection.commit();
                                        throw new SQLTransientConnectionException("SYNTHETIC_ACK_LOSS", "08006");
                                    }
                                    return invoke(connection, cm, ca);
                                });
                    }
                    return result;
                });
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
