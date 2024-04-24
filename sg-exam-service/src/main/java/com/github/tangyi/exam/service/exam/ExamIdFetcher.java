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

package com.github.tangyi.exam.service.exam;

import com.github.tangyi.common.base.id.IdFetcher;
import com.github.tangyi.exam.mapper.ExaminationMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ExamIdFetcher extends IdFetcher {

	private final ExaminationMapper mapper;

	public ExamIdFetcher(ExaminationMapper mapper) {
		this.mapper = mapper;
	}

	@Override
	public List<Long> fetchPage(long minId, Map<String, Object> params) {
		int pageSize = Integer.parseInt(params.getOrDefault("pageSize", "100").toString());
		return this.mapper.findIdsOrderByIdAsc(minId, pageSize, params);
	}
}
