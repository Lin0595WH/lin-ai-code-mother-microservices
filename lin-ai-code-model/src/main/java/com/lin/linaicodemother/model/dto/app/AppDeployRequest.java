package com.lin.linaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 应用部署请求
 */
@Data
public class AppDeployRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应用 id
     */
    private Long appId;

}
