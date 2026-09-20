package com.zhku.knowledge.serviceguide;

import java.util.Optional;

/**
 * 清单仓储端口：只暴露"已发布且学生可见"的查询。
 *
 * <p><b>为什么端口只提供这一个查询</b>：解析器对"未发布 / 内部参考 / 资格待确认 / 已被替代"
 * 的处理完全一致（转人工），不区分原因——把区别留给仓储层，解析器就不会因为多知道一点
 * 内部状态而把它们泄进回答。</p>
 */
public interface ServiceGuideRepository {

    /**
     * 查找指定标识与版本的、当前已发布且学生可见的清单；不存在或不可见时返回空。
     */
    Optional<ServiceGuide> findPublished(String guideIdentifier, String guideVersion);
}
