package com.github.tangyi.exam.excel;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;
import com.github.tangyi.exam.enums.SubjectLevel;
import com.github.tangyi.exam.enums.SubjectType;
import com.github.tangyi.exam.enums.SubmitStatus;

public final class Converters {

	private Converters() {

	}

	public static final class TypeConverter implements Converter<Integer> {

		@Override
		public Class<?> supportJavaTypeKey() {
			return Integer.class;
		}

		@Override
		public CellDataTypeEnum supportExcelTypeKey() {
			return CellDataTypeEnum.STRING;
		}

		@Override
		public Integer convertToJavaData(ReadCellData<?> cellData, ExcelContentProperty contentProperty,
				GlobalConfiguration globalConfiguration) {
			return SubjectType.matchByName(cellData.getStringValue()).getValue();
		}

		@Override
		public WriteCellData<String> convertToExcelData(Integer value, ExcelContentProperty contentProperty,
				GlobalConfiguration globalConfiguration) {
			return new WriteCellData<>(SubjectType.matchByValue(value).getName());
		}
	}

	public static final class SubmitConverter implements Converter<Integer> {

		@Override
		public Class<?> supportJavaTypeKey() {
			return Integer.class;
		}

		@Override
		public CellDataTypeEnum supportExcelTypeKey() {
			return CellDataTypeEnum.STRING;
		}

		@Override
		public Integer convertToJavaData(ReadCellData<?> cellData, ExcelContentProperty contentProperty,
				GlobalConfiguration globalConfiguration) {
			return SubmitStatus.matchByName(cellData.getStringValue()).getValue();
		}

		@Override
		public WriteCellData<String> convertToExcelData(Integer value, ExcelContentProperty contentProperty,
				GlobalConfiguration globalConfiguration) {
			return new WriteCellData<>(SubmitStatus.matchByValue(value).getName());
		}
	}

	/**
	 * 题目难度级别
	 */
	public static final class LevelConverter implements Converter<Integer> {

		@Override
		public Class<?> supportJavaTypeKey() {
			return Integer.class;
		}

		@Override
		public CellDataTypeEnum supportExcelTypeKey() {
			return CellDataTypeEnum.STRING;
		}

		@Override
		public Integer convertToJavaData(ReadCellData<?> cellData, ExcelContentProperty contentProperty,
				GlobalConfiguration globalConfiguration) {
			return SubjectLevel.matchByName(cellData.getStringValue()).getValue();
		}

		@Override
		public WriteCellData<String> convertToExcelData(Integer value, ExcelContentProperty contentProperty,
				GlobalConfiguration globalConfiguration) {
			return new WriteCellData<>(SubjectLevel.matchByValue(value).getName());
		}
	}
}
