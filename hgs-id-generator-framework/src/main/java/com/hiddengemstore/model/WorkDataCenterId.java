package com.hiddengemstore.model;

import lombok.Data;

/**
 * 工作节点ID和数据中心ID的数据模型
 * @author : Zhao
 */
@Data
public class WorkDataCenterId {
    // 工作节点id
    private Long workId;
    // 数据中心id
    private Long dataCenterId;
}
