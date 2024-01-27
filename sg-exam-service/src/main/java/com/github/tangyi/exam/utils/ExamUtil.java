package com.github.tangyi.exam.utils;

import com.github.tangyi.api.exam.dto.ExaminationRecordDto;
import com.github.tangyi.api.exam.dto.SimpleSubjectDto;
import com.github.tangyi.api.exam.dto.SimpleSubjectOptionDto;
import com.github.tangyi.api.exam.dto.SubjectDto;
import com.github.tangyi.api.exam.model.Answer;
import com.github.tangyi.api.exam.model.ExaminationSubject;
import com.github.tangyi.api.exam.model.SubjectOption;
import com.github.tangyi.api.exam.model.Subjects;
import com.github.tangyi.exam.enums.SubjectType;
import com.github.tangyi.exam.excel.ExamRecordModel;
import com.github.tangyi.exam.excel.SubjectExcelModel;
import com.github.tangyi.exam.handler.HandlerFactory;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class ExamUtil {

	private static final String REGEX_COMMA = "^,*|,*$";

	private ExamUtil() {
	}

	public static ExaminationSubject createEs(Long examinationId, Long subjectId) {
		ExaminationSubject es = new ExaminationSubject();
		es.setExaminationId(examinationId);
		es.setSubjectId(subjectId);
		return es;
	}

	public static List<SimpleSubjectDto> simpleSubject(List<SubjectDto> dtoList) {
		List<SimpleSubjectDto> simples = Lists.newArrayListWithExpectedSize(dtoList.size());
		for (SubjectDto dto : dtoList) {
			SimpleSubjectDto simple = new SimpleSubjectDto();
			simple.setId(dto.getId());
			simple.setSubjectName(dto.getSubjectName());
			simple.setTypeLabel(dto.getTypeLabel());
			simple.setType(dto.getType());
			simple.setChoicesType(dto.getChoicesType());
			simple.setScore(dto.getScore());
			simple.setLevel(dto.getLevel());
			simple.setSort(dto.getSort());
			simple.setSpeechUrl(dto.getSpeechUrl());
			simple.setAutoPlaySpeech(dto.getAutoPlaySpeech());
			simple.setSpeechPlayLimit(dto.getSpeechPlayLimit());
			simple.setSubjectVideoUrl(dto.getSubjectVideoUrl());

			List<SimpleSubjectOptionDto> optionDtoList = Lists.newArrayList();
			List<SubjectOption> options = dto.getOptions();
			if (CollectionUtils.isNotEmpty(options)) {
				for (SubjectOption option : options) {
					SimpleSubjectOptionDto optionDto = new SimpleSubjectOptionDto();
					optionDto.setOptionName(option.getOptionName());
					optionDto.setOptionContent(option.getOptionContent());
					optionDto.setSort(option.getSort());
					optionDtoList.add(optionDto);
				}
			}
			simple.setOptions(optionDtoList);
			simples.add(simple);
		}
		return simples;
	}

	public static HandlerFactory.Result addAll(List<HandlerFactory.Result> results) {
		HandlerFactory.Result result = new HandlerFactory.Result();
		int score = 0;
		int correctNum = 0;
		int inCorrectNum = 0;
		boolean hasHumanJudgeSubject = false;
		for (HandlerFactory.Result r : results) {
			if (r != null) {
				score += r.getScore();
				correctNum += r.getCorrectNum();
				inCorrectNum += r.getInCorrectNum();
				if (r.isHasHumanJudgeSubject()) {
					hasHumanJudgeSubject = true;
				}
			}
		}
		result.setScore(score);
		result.setCorrectNum(correctNum);
		result.setInCorrectNum(inCorrectNum);
		result.setHasHumanJudgeSubject(hasHumanJudgeSubject);
		return result;
	}

	public static String replaceComma(String str) {
		if (StringUtils.isNotBlank(str)) {
			str = str.replaceAll(REGEX_COMMA, "");
		}
		return str;
	}

	public static Map<Integer, List<Answer>> distinctAnswer(List<Answer> answers, Map<Long, Integer> typeMap) {
		Map<Integer, List<Answer>> map = Maps.newHashMap();
		map.putAll(answers.stream()
				.collect(Collectors.groupingBy(s -> typeMap.get(s.getSubjectId()), Collectors.toList())));
		return map;
	}

	public static List<ExamRecordModel> convertExamRecord(List<ExaminationRecordDto> records) {
		List<ExamRecordModel> models = new ArrayList<>(records.size());
		records.forEach(r -> {
			ExamRecordModel model = new ExamRecordModel();
			BeanUtils.copyProperties(r, model);
			models.add(model);
		});
		return models;
	}

	public static List<SubjectExcelModel> convertSubject(List<SubjectDto> dtoList) {
		List<SubjectExcelModel> models = Lists.newArrayListWithExpectedSize(dtoList.size());
		dtoList.forEach(s -> {
			SubjectExcelModel model = new SubjectExcelModel();
			BeanUtils.copyProperties(s, model);
			if (CollectionUtils.isNotEmpty(s.getOptions())) {
				for (SubjectOption o : s.getOptions()) {
					switch (o.getOptionName()) {
						case "A" -> model.setOptionA(o.getOptionContent());
						case "B" -> model.setOptionB(o.getOptionContent());
						case "C" -> model.setOptionC(o.getOptionContent());
						case "D" -> model.setOptionD(o.getOptionContent());
						default -> {}
					}
				}
			}
			model.setAnswer(s.getAnswer().getAnswer());
			models.add(model);
		});
		return models;
	}

	/**
	 * 遍历关系集合，按类型分组题目 ID，返回 map
	 */
	public static Map<String, Long[]> groupByType(List<Subjects> subjects) {
		Map<String, Long[]> idMap = Maps.newHashMapWithExpectedSize(4);
		subjects.stream().collect(Collectors.groupingBy(Subjects::getType, Collectors.toList()))
				.forEach((type, temp) -> {
					if (SubjectType.CHOICES.getValue() == type) {
						idMap.put(SubjectType.CHOICES.name(),
								temp.stream().map(Subjects::getSubjectId).distinct().toArray(Long[]::new));
					} else if (SubjectType.JUDGEMENT.getValue() == type) {
						idMap.put(SubjectType.JUDGEMENT.name(),
								temp.stream().map(Subjects::getSubjectId).distinct().toArray(Long[]::new));
					} else if (SubjectType.MULTIPLE_CHOICES.getValue() == type) {
						idMap.put(SubjectType.MULTIPLE_CHOICES.name(),
								temp.stream().map(Subjects::getSubjectId).distinct().toArray(Long[]::new));
					} else if (SubjectType.SHORT_ANSWER.getValue() == type) {
						idMap.put(SubjectType.SHORT_ANSWER.name(),
								temp.stream().map(Subjects::getSubjectId).distinct().toArray(Long[]::new));
					} else {
						log.error("Unknown subject type: {}", type);
					}
				});
		return idMap;
	}

	public static Map<Long, Integer> toMap(List<Subjects> subjects) {
		return subjects.stream().collect(Collectors.toMap(Subjects::getSubjectId, Subjects::getType));
	}
}
