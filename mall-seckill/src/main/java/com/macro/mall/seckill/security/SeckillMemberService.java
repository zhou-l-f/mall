package com.macro.mall.seckill.security;

import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberExample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀会员鉴权与当前登录人解析
 * JWT 中只保存用户名，这里通过用户名换取 memberId，并用 Redis 短时缓存避免每次请求打 DB
 */
@Service
public class SeckillMemberService implements UserDetailsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeckillMemberService.class);

    private static final String MEMBER_CACHE_PREFIX = "seckill:member:";
    private static final long MEMBER_CACHE_SECONDS = 3600L;

    @Autowired
    private UmsMemberMapper memberMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SeckillMemberDetails cached = getFromCache(username);
        if (cached != null) {
            return cached;
        }
        UmsMember member = selectByUsername(username);
        if (member == null) {
            throw new UsernameNotFoundException("会员不存在:" + username);
        }
        boolean enabled = Integer.valueOf(1).equals(member.getStatus());
        putToCache(member);
        return new SeckillMemberDetails(member.getId(), member.getUsername(), member.getPassword(),
                enabled, AuthorityUtils.createAuthorityList("USER"));
    }

    /**
     * 获取当前登录会员ID
     */
    public Long getCurrentMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SeckillMemberDetails) {
            return ((SeckillMemberDetails) principal).getMemberId();
        }
        SeckillMemberDetails details = getFromCache(authentication.getName());
        if (details != null) {
            return details.getMemberId();
        }
        UmsMember member = selectByUsername(authentication.getName());
        if (member == null) {
            return null;
        }
        putToCache(member);
        return member.getId();
    }

    /**
     * 获取当前登录用户名
     */
    public String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }

    private UmsMember selectByUsername(String username) {
        UmsMemberExample example = new UmsMemberExample();
        example.createCriteria().andUsernameEqualTo(username);
        List<UmsMember> members = memberMapper.selectByExample(example);
        return CollectionUtils.isEmpty(members) ? null : members.get(0);
    }

    private SeckillMemberDetails getFromCache(String username) {
        try {
            String value = stringRedisTemplate.opsForValue().get(MEMBER_CACHE_PREFIX + username);
            if (value == null) {
                return null;
            }
            String[] parts = value.split("\\|", -1);
            if (parts.length < 2) {
                return null;
            }
            Long memberId = Long.valueOf(parts[0]);
            boolean enabled = "1".equals(parts[1]);
            return new SeckillMemberDetails(memberId, username, "", enabled,
                    AuthorityUtils.createAuthorityList("USER"));
        } catch (Exception e) {
            LOGGER.warn("读取会员缓存失败, username={}", username, e);
            return null;
        }
    }

    private void putToCache(UmsMember member) {
        try {
            String value = member.getId() + "|" + member.getStatus();
            stringRedisTemplate.opsForValue().set(MEMBER_CACHE_PREFIX + member.getUsername(), value,
                    MEMBER_CACHE_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.warn("写入会员缓存失败, username={}", member.getUsername(), e);
        }
    }
}
