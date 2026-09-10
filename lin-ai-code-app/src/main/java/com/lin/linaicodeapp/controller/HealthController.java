package com.lin.linaicodeapp.controller;


import com.lin.linaicodemother.common.BaseResponse;
import com.lin.linaicodemother.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author Lin
 * @Date 2025/12/15 21:15
 * @Descriptions 健康检查
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping("/")
    public BaseResponse<String> healthCheck() {
        return ResultUtils.success("ok");
    }
}
