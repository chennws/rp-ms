package com.ruoyi.system.service.impl;

import com.onlyoffice.manager.security.JwtManager;

import com.ruoyi.common.core.domain.pojo.DownloadResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.MinioException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Author: Li
 * Date: 10/25/2025 17:18
 * Description: DocumentServiceImpl
 */
@Service
@Slf4j
public class OfficeServiceImpl {

    @Resource
    private JwtManager jwtManager;
    @Value("${minio.endpoint}")
    private String endpoint;
    @Value("${minio.bucket-name}")
    private String bucketName;
    @Value("${docservice.watermark.enable}")
    private boolean watermarkEnable;
    @Value("${docservice.url}")
    private String officeRequestUrl;

    @Resource
    private MinioClient minioClient;

    /**
     * 将docx文档转换成pdf文档，转换完成后将pdf上传至minio中
     * @param fileUrl
     * @return
     * @throws IOException
     * @throws MinioException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    public String coverToPdf(String fileUrl) throws IOException, MinioException, NoSuchAlgorithmException, InvalidKeyException {
        String key = this.getFileKey(fileUrl);
        // 用户指定的下载类型
        String ext = this.getExtension(fileUrl);
        Map<String, Object> params = new HashMap<>();
        params.put("async", false);
        params.put("title", "download");
        params.put("key", key);
        params.put("url", fileUrl);
        params.put("filetype", ext);
        params.put("outputtype", "pdf");
        if(this.watermarkEnable) {
            Map<String, Object> watermark = new HashMap<>();
            watermark.put("align", 1);
//            watermark.put("fill", Arrays.asList(255, 0, 0));
            // 水印高度
            watermark.put("height", 50);
            // 水印宽度
            watermark.put("width", 150);
            // 水印旋转角度
            watermark.put("rotate", -20);
            // 水印透明度
            watermark.put("transparent", 0.3);
            watermark.put("type", "rect");
            watermark.put("stroke-width", 0);
            List<Map<String, Object>> paragraphs = new ArrayList<>(1);
            Map<String, Object> paragraph = new HashMap<>();
            // 水印居中
            paragraph.put("align", 2);
//            paragraph.put("fill", Arrays.asList(255, 0, 0));
            // 行间距
            paragraph.put("linespacing", 2);
            List<Map<String, Object>> runs = new ArrayList<>(2);
            Map<String, Object> run = new HashMap<>();
            run.put("bold", true);
            run.put("font-family", "宋体");
            // 水印字体大小
            run.put("font-size", 30);
            // @TODO替换水印
            run.put("text", "水印水印   水印水印");
            run.put("fill", Arrays.asList(0, 0, 0));
            // 是否有下划线
            run.put("underline", false);
            Map<String, Object> run2 = new HashMap<>();
            // 插入换行
            run2.put("text", "<%br%>");
            // 添加两行水印
            runs.add(run);
            runs.add(run2);
            runs.add(run);
            paragraph.put("runs", runs);
            paragraphs.add(paragraph);
            watermark.put("paragraphs", paragraphs);
            params.put("watermark", watermark);
        }
        String token = this.jwtManager.createToken(params);
        Map<String, String> requestData = new HashMap<>();
        requestData.put("token", token);
        RestTemplate restTemplate = new RestTemplate();
        String requestUrl = this.officeRequestUrl + "converter";
        ResponseEntity<DownloadResponse> downloadRes = restTemplate.postForEntity(requestUrl, requestData, DownloadResponse.class);
        DownloadResponse result = downloadRes.getBody();
        if (result == null) {
            log.error("pdf转换失败");
            throw new RuntimeException("pdf转换失败");
        } else {
            String downloadFileUrl = result.getFileUrl();
            ResponseEntity<byte[]> response = restTemplate.getForEntity(downloadFileUrl, byte[].class);
            byte[] data = response.getBody();
            if(data == null) {
                log.error("pdf下载失败");
                throw new RuntimeException("文件下载失败");
            }
            try(InputStream inputStream =  new ByteArrayInputStream(response.getBody())) {
                LocalDateTime date = LocalDateTime.now();
                int year = date.getYear();
                int month = date.getMonthValue() + 1;
                int day = date.getDayOfMonth();
                String newName = UUID.randomUUID() + ".pdf";
                String objectName = String.format("/%s/%s/%s/%s", year, month, day, newName);
                String contentType = "application/octet-stream";
                PutObjectArgs args = PutObjectArgs.builder()
                        .bucket(this.bucketName)
                        .object(objectName)
                        .stream(inputStream, data.length, -1)
                        .contentType(contentType)
                        .build();
                this.minioClient.putObject(args);
                String url = String.format("%s/%s%s", this.endpoint, this.bucketName, objectName);
                return url;
            }
        }
    }

    /**
     * 将docx文档转换成pdf文档，转换完成后直接传输给前端下载
     * @param fileUrl
     * @throws IOException
     * @throws MinioException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    public void coverToPdf(String fileUrl, HttpServletResponse response) throws IOException, MinioException, NoSuchAlgorithmException, InvalidKeyException {
        String key = this.getFileKey(fileUrl);
        // 用户指定的下载类型
        String ext = this.getExtension(fileUrl);
        Map<String, Object> params = new HashMap<>();
        params.put("async", false);
        params.put("title", "download");
        params.put("key", key);
        params.put("url", fileUrl);
        params.put("filetype", ext);
        params.put("outputtype", "pdf");
        if(this.watermarkEnable) {
            Map<String, Object> watermark = new HashMap<>();
            watermark.put("align", 1);
//            watermark.put("fill", Arrays.asList(255, 0, 0));
            // 水印高度
            watermark.put("height", 50);
            // 水印宽度
            watermark.put("width", 150);
            // 水印旋转角度
            watermark.put("rotate", -20);
            // 水印透明度
            watermark.put("transparent", 0.3);
            watermark.put("type", "rect");
            watermark.put("stroke-width", 0);
            List<Map<String, Object>> paragraphs = new ArrayList<>(1);
            Map<String, Object> paragraph = new HashMap<>();
            // 水印居中
            paragraph.put("align", 2);
//            paragraph.put("fill", Arrays.asList(255, 0, 0));
            // 行间距
            paragraph.put("linespacing", 2);
            List<Map<String, Object>> runs = new ArrayList<>(2);
            Map<String, Object> run = new HashMap<>();
            run.put("bold", true);
            run.put("font-family", "宋体");
            // 水印字体大小
            run.put("font-size", 30);
            // @TODO替换水印
            run.put("text", "水印水印   水印水印");
            run.put("fill", Arrays.asList(0, 0, 0));
            // 是否有下划线
            run.put("underline", false);
            Map<String, Object> run2 = new HashMap<>();
            // 插入换行
            run2.put("text", "<%br%>");
            // 添加两行水印
            runs.add(run);
            runs.add(run2);
            runs.add(run);
            paragraph.put("runs", runs);
            paragraphs.add(paragraph);
            watermark.put("paragraphs", paragraphs);
            params.put("watermark", watermark);
        }
        String token = this.jwtManager.createToken(params);
        Map<String, String> requestData = new HashMap<>();
        requestData.put("token", token);
        RestTemplate restTemplate = new RestTemplate();
        String requestUrl = this.officeRequestUrl + "converter";
        ResponseEntity<DownloadResponse> downloadRes = restTemplate.postForEntity(requestUrl, requestData, DownloadResponse.class);
        DownloadResponse result = downloadRes.getBody();
        if (result == null) {
            log.error("pdf转换失败");
            throw new RuntimeException("pdf转换失败");
        } else {
            String downloadFileUrl = result.getFileUrl();
            // @TODO 修改下载的文件名
            String filename = URLEncoder.encode("下载的文件名.pdf", "UTF-8");
            response.setContentType("application/octet-stream");
            response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
            response.setHeader("Content-Disposition", "attachment;filename=" + filename);
            try (OutputStream out = response.getOutputStream()) {
                restTemplate.execute(downloadFileUrl, HttpMethod.GET, null, clientHttpResponse -> {
                    try (InputStream inputStream = clientHttpResponse.getBody()) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                    return null;
                });
            }
        }
    }

    private String getFileKey(String fileUrl) {
        try {
            String objectName = fileUrl.replace(this.endpoint + "/" + this.bucketName + "/", "");
            StatObjectResponse stat = this.minioClient.statObject(StatObjectArgs.builder()
                    .bucket(this.bucketName)
                    .object(objectName)
                    .build());
            ZonedDateTime zonedDateTime = stat.lastModified();
            // 将 ZonedDateTime 转换为 Instant
            Instant instant = zonedDateTime.toInstant();
            // 获取时间戳（毫秒）
            long timestamp = instant.toEpochMilli();
            String key = DigestUtils.sha256Hex(objectName + timestamp);
            return key;
        } catch (MinioException | IOException | InvalidKeyException | NoSuchAlgorithmException e) {
            log.error("获取文件信息失败", e);
            throw new RuntimeException(e);
        }
    }

    private String getExtension(String fileUrl) {
        String suffix = fileUrl.substring(fileUrl.lastIndexOf(".") + 1);
        if (StringUtils.isEmpty(suffix)) {
            throw new RuntimeException("无法读取文件扩展名");
        }
        String ext = suffix.split("\\?")[0];
        if (!Pattern.matches("docx|pptx|xlsx|pdf", ext)) {
            throw new RuntimeException("文件扩展名不合法");
        }
        return ext;
    }
}
