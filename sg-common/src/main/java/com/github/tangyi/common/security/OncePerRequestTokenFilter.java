package com.github.tangyi.common.security;

import com.beust.jcommander.internal.Lists;
import com.github.tangyi.common.base.SgPreconditions;
import com.github.tangyi.common.constant.ApiMsg;
import com.github.tangyi.common.constant.CommonConstant;
import com.github.tangyi.common.constant.SecurityConstant;
import com.github.tangyi.common.exceptions.CommonException;
import com.github.tangyi.common.model.CustomUserDetails;
import com.github.tangyi.common.model.R;
import com.github.tangyi.common.model.UserToken;
import com.github.tangyi.common.properties.FilterIgnorePropertiesConfig;
import com.github.tangyi.common.security.exceptions.TokenExpireException;
import com.github.tangyi.common.security.exceptions.TokenInvalidException;
import com.github.tangyi.common.utils.ObjectUtil;
import com.github.tangyi.common.utils.RUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class OncePerRequestTokenFilter extends OncePerRequestFilter {

	private static final int BEARER_LENGTH = SecurityConstant.BEARER.length();

	private final TokenManager tokenManager;
	private final List<AntPathRequestMatcher> matchers;

	public OncePerRequestTokenFilter(TokenManager tokenManager,
			FilterIgnorePropertiesConfig filterIgnorePropertiesConfig) {
		this.tokenManager = tokenManager;
		this.matchers = Lists.newArrayList();
		List<String> urls = filterIgnorePropertiesConfig.getUrls();
		if (CollectionUtils.isNotEmpty(urls)) {
			for (String url : urls) {
				matchers.add(new AntPathRequestMatcher(url));
			}
		}
	}

	@Override
	protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
			throws ServletException, IOException {
		if (this.isIgnoreUrl(req)) {
			chain.doFilter(req, res);
			return;
		}

		try {
			this.parseToken(req);
			chain.doFilter(req, res);
		} catch (CommonException e) {
			RUtil.out(res, R.error(ApiMsg.KEY_TOKEN, e.getMessage()));
		}
	}

	private boolean isIgnoreUrl(HttpServletRequest request) {
		for (AntPathRequestMatcher matcher : this.matchers) {
			if (matcher.matcher(request).isMatch()) {
				return true;
			}
		}
		return false;
	}

	private void parseToken(HttpServletRequest request) {
		String authorization = this.getAuthorization(request);
		String token = authorization.substring(BEARER_LENGTH);
		Claims claims = this.parseToken(token);
		String identify = ObjectUtil.toString(claims.get(TokenManager.IDENTIFY));
		if (StringUtils.isNotEmpty(identify) && SecurityContextHolder.getContext().getAuthentication() == null) {
			String userId = ObjectUtil.toString(claims.get(TokenManager.USER_ID));
			String id = ObjectUtil.toString(claims.get(TokenManager.ID));
			String tenantCode = ObjectUtil.toString(claims.get(TokenManager.TENANT_CODE));
			UserToken userToken = this.tokenManager.getToken(userId);
			if (userToken == null) {
				throw new TokenExpireException("token 已失效，请重新登录");
			}

			if (!userToken.getUserId().toString().equals(userId)) {
				throw new TokenExpireException("token 校验失败，请重新登录");
			}

			if (!userToken.getId().equals(id)) {
				throw new TokenExpireException("token 已失效");
			}

			this.updateExpireSecondsIfNecessary(userToken);
			this.setAuthentication(userToken, userId, identify, tenantCode);
		}
	}

	private String getAuthorization(HttpServletRequest request) {
		String authorization = request.getHeader(SecurityConstant.AUTHORIZATION);
		if (StringUtils.isEmpty(authorization)) {
			authorization = request.getParameter(SecurityConstant.AUTHORIZATION);
		}
		SgPreconditions.checkBoolean(StringUtils.isEmpty(authorization), "无用户凭证");
		SgPreconditions.checkBoolean(!authorization.startsWith(SecurityConstant.BEARER), "token非法");
		return authorization;
	}

	private Claims parseToken(String token) {
		try {
			return this.tokenManager.getTokenBody(token);
		} catch (Exception e) {
			throw new TokenInvalidException(e, "Failed to parse token.");
		}
	}

	private void updateExpireSecondsIfNecessary(UserToken userToken) {
		Duration duration = Duration.between(LocalDateTime.now(), userToken.getExpiresAt());
		// token 接近过期则自动续期
		if (duration.toMinutes() < TokenManager.TOKEN_AUTO_RESET_EXPIRE_MINUTE) {
			// "记住我" 续期 7 天
			long expireSeconds = userToken.isRemember() ?
					TimeUnit.DAYS.toSeconds(TokenManager.TOKEN_REMEMBER_EXPIRE_DAY) :
					TimeUnit.MINUTES.toSeconds(TokenManager.TOKEN_EXPIRE_MINUTE);
			log.info("Reset expire seconds, userId: {}, expireSeconds: {}", userToken.getUserId(), expireSeconds);
			this.tokenManager.updateExpireSeconds(userToken, (int) expireSeconds);
		}
	}

	private void setAuthentication(UserToken userToken, String userId, String identify, String tenantCode) {
		String role = userToken.getRole();
		Collection<GrantedAuthority> authorities = Lists.newArrayList();
		if (StringUtils.isNotEmpty(role)) {
			for (String r : role.split(CommonConstant.COMMA)) {
				authorities.add((GrantedAuthority) () -> r);
			}
		}
		CustomUserDetails details = new CustomUserDetails(Long.valueOf(userId), identify, authorities, tenantCode);
		JwtAuthenticationToken authentication = new JwtAuthenticationToken(details, authorities);
		authentication.setAuthenticated(true);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}
}
