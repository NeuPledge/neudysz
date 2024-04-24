/*
 * Copyright 2024 The sg-exam authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.tangyi.auth.security.mobile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tangyi.auth.security.core.user.CustomUserDetailsService;
import com.github.tangyi.auth.service.UserTokenService;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 手机登录配置
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MobileSecurityConfigurer extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private AuthenticationEventPublisher defaultAuthenticationEventPublisher;

	@Autowired
	private CustomUserDetailsService userDetailsService;

	@Autowired
	private UserTokenService userTokenService;

	@Override
	public void configure(HttpSecurity http) {
		MobileLoginFilter filter = new MobileLoginFilter(userTokenService);
		filter.setAuthenticationManager(http.getSharedObject(AuthenticationManager.class));
		filter.setEventPublisher(defaultAuthenticationEventPublisher);
		MobileAuthenticationProvider mobileAuthenticationProvider = new MobileAuthenticationProvider();
		mobileAuthenticationProvider.setCustomUserDetailsService(userDetailsService);
		http.authenticationProvider(mobileAuthenticationProvider)
				.addFilterAfter(filter, UsernamePasswordAuthenticationFilter.class);
	}
}
