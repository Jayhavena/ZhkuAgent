package com.zhku.knowledge.notice;

import java.util.List;

/**
 * 通知读取边界。
 *
 * <p>当前性判断留在领域服务，仓储不能以数据库时间或“最新一条”替代受控 Clock 判定。</p>
 */
@FunctionalInterface
public interface NoticeRepository {

    List<PublishedNotice> listNotices();
}
