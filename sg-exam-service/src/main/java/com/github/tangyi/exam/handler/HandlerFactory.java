package com.github.tangyi.exam.handler;

import com.github.tangyi.api.exam.model.Answer;
import com.github.tangyi.common.utils.SpringContextHolder;
import com.github.tangyi.exam.enums.SubjectType;
import com.github.tangyi.exam.utils.ExamUtil;
import com.google.common.collect.Lists;
import lombok.Data;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;

public class HandlerFactory {

	private static ApplicationContext CONTEXT;

	public static IAnswerHandler getHandler(int type) {
		if (CONTEXT == null) {
			CONTEXT = SpringContextHolder.getApplicationContext();
		}
		return CONTEXT.getBean(SubjectType.matchByValue(type).getHandler());
	}

	public static Result handleAll(Map<Integer, List<Answer>> map) {
		List<Result> results = Lists.newArrayListWithExpectedSize(map.size());
		for (Map.Entry<Integer, List<Answer>> entry : map.entrySet()) {
			IAnswerHandler handler = HandlerFactory.getHandler(entry.getKey());
			Result result = handler.handle(entry.getValue());
			if (result != null) {
				results.add(result);
			}
		}
		return ExamUtil.addAll(results);
	}

	@Data
	public static final class Result {

		/**
		 * 总分
		 */
		private double score;

		/**
		 * 正确题目数
		 */
		private int correctNum;

		/**
		 * 错误题目数
		 */
		private int inCorrectNum;

		/**
		 * 是否有人工判分的题目
		 */
		private boolean hasHumanJudgeSubject;
	}
}
