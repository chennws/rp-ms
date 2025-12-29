package com.ruoyi.system.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlyoffice.manager.security.JwtManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Map;

/**
 * Author: Li
 * Date: 6/15/2025 23:12
 * Description: jwt签名校验，如果开启了签名认证，配置信息和回调数据将会使用这个类进行签名 | 校验
 */
@Component
public class DefaultJwtManager implements JwtManager {
    @Value("${docservice.security.enable}")
    private Boolean securityEnable;
    @Value("${docservice.security.key}")
    private String secretKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String createToken(Object object) {
        Map<String, ?> payloadMap = (Map)this.objectMapper.convertValue(object, Map.class);
        return this.createToken(payloadMap, this.secretKey);
    }
    @Override
    public String createToken(Object object, String key) {
        Map<String, ?> payloadMap = (Map)this.objectMapper.convertValue(object, Map.class);
        return this.createToken(payloadMap, key);
    }
    @Override
    public String createToken(Map<String, ?> payloadMap, String key) {
        Algorithm algorithm = Algorithm.HMAC256(key);
        String token = JWT.create().withPayload(payloadMap).sign(algorithm);
        return token;
    }
    @Override
    public String verify(String token) {
        return this.verifyToken(token, this.secretKey);
    }
    @Override
    public String verifyToken(String token, String key) {
        Base64.Decoder decoder = Base64.getUrlDecoder();
        Algorithm algorithm = Algorithm.HMAC256(key);
        // 验证token
        DecodedJWT jwt = JWT.require(algorithm).acceptLeeway(1).build().verify(token);
        return new String(decoder.decode(jwt.getPayload()));
    }
}
