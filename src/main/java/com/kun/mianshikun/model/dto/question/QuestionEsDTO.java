package com.kun.mianshikun.model.dto.question;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.kun.mianshikun.model.entity.Question;
import lombok.Data;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Document(indexName = "question", createIndex = false)
@Data
public class QuestionEsDTO implements Serializable {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private static final String ANALYZER = "ik_max_word";
    private static final String SEARCH_ANALYZER = "ik_smart";

    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = ANALYZER, searchAnalyzer = SEARCH_ANALYZER)
    private String title;

    @Field(type = FieldType.Text, analyzer = ANALYZER, searchAnalyzer = SEARCH_ANALYZER)
    private String content;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    @Field(type = FieldType.Text, analyzer = ANALYZER, searchAnalyzer = SEARCH_ANALYZER)
    private String answer;

    @Field(type = FieldType.Long)
    private Long userId;
    @Field(type = FieldType.Date, format = {}, pattern = DATE_TIME_PATTERN)
    private Date editTime;
    @Field(type = FieldType.Date, format = {}, pattern = DATE_TIME_PATTERN)
    private Date createTime;

    @Field(type = FieldType.Date, format = {}, pattern = DATE_TIME_PATTERN)
    private Date updateTime;

    @Field(type = FieldType.Keyword)
    private Integer isDelete;

    private static final long serialVersionUID = 1L;

    public static QuestionEsDTO objToDto(Question question) {
        if (question == null) {
            return null;
        }
        QuestionEsDTO questionEsDTO = new QuestionEsDTO();
        BeanUtils.copyProperties(question, questionEsDTO);
        String tagsStr = question.getTags();
        if (StringUtils.isNotBlank(tagsStr)) {
            questionEsDTO.setTags(JSONUtil.toList(tagsStr, String.class));
        }
        return questionEsDTO;
    }

    public static Question dtoToObj(QuestionEsDTO questionEsDTO) {
        if (questionEsDTO == null) {
            return null;
        }
        Question question = new Question();
        BeanUtils.copyProperties(questionEsDTO, question);
        List<String> tagList = questionEsDTO.getTags();
        if (CollUtil.isNotEmpty(tagList)) {
            question.setTags(JSONUtil.toJsonStr(tagList));
        }
        return question;
    }
}
