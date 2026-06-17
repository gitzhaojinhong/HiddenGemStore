package com.hiddengemstore.service;

/**
 * @author : ZhaoJH
 */
public interface ISeckillAccessTokenService {

    boolean isEnabled();

    String issueAccessToken(Long voucherId, Long userId);

    boolean validateAndConsume(Long voucherId, Long userId, String token);

}
