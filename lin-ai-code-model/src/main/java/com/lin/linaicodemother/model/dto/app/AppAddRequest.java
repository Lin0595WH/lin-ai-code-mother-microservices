package com.lin.linaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 应用创建请求
 */
@Data
public class AppAddRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应用初始化的 prompt
     */
    private String initPrompt;

} 