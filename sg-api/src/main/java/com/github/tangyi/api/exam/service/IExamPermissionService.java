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

package com.github.tangyi.api.exam.service;

import com.github.tangyi.api.exam.dto.MemberDto;
import com.github.tangyi.api.exam.model.ExamPermission;
import com.github.tangyi.common.service.ICrudService;

import java.util.List;

public interface IExamPermissionService extends ICrudService<ExamPermission> {

	List<ExamPermission> findPermissionList(Integer examType, Long examId);

	Integer findCount(Integer examType, Long examId);

	MemberDto getMembers(Integer examType, Long examId);

	int insertBatch(List<ExamPermission> members);

	void deletePermission(Integer examType, Long memberId);

}
