package com.lin.linaicodeapp.service;


import jakarta.servlet.http.HttpServletResponse;

/**
 * @Author Lin
 * @Date 2026/3/25 21:00
 * @Descriptions 项目下载
 */
public interface ProjectDownloadService {

    /**
     * 下载项目为压缩包
     *
     * @param projectPath
     * @param downloadFileName
     * @param response
     */
    void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response);
}
