package com.macro.mall.seckill.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * 秒杀会员登录信息
 */
public class SeckillMemberDetails extends User {

    private static final long serialVersionUID = 1L;

    private final Long memberId;

    public SeckillMemberDetails(Long memberId, String username, String password,
                                boolean enabled, Collection<? extends GrantedAuthority> authorities) {
        super(username, password == null ? "" : password, enabled, true, true, true, authorities);
        this.memberId = memberId;
    }

    public Long getMemberId() {
        return memberId;
    }
}
