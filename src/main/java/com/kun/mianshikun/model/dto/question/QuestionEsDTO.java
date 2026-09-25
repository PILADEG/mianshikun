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

/**
 * 题目 ES 包装类
 * <p>
 * 索引由脚本管理（见 sql/question_es_mapping.json），因此 createIndex = false：
 * 避免框架按实体注解"自动建索引"时因注解缺少 analyzer 而把中文检索退化为标准分词器。
 * 同时这里仍显式声明 analyzer，作为「框架建索引」场景下的双保险。
 */
@Document(indexName = "question", createIndex = false)
@Data
public class QuestionEsDTO implements Serializable {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 索引与查询均使用 IK 分词：索引细粒度（ik_max_word）、查询粗粒度（ik_smart） */
    private static final String ANALYZER = "ik_max_word";
    private static final String SEARCH_ANALYZER = "ik_smart";

    /**
     * id
     */
    @Id
    private Long id;

    /**
     * 标题
     */
    @Field(type = FieldType.Text, analyzer = ANALYZER, searchAnalyzer = SEARCH_ANALYZER)
    private String title;

    /**
     * 内容
     */
    @Field(type = FieldType.Text, analyzer = ANALYZER, searchAnalyzer = SEARCH_ANALYZER)
    private String content;

    /**
     * 标签列表（keyword：与 termQuery 精确匹配的用法一致）
     */
    @Field(type = FieldType.Keyword)
    private List<String> tags;

    /**
     * 答案
     */
    @Field(type = FieldType.Text, analyzer = ANALYZER, searchAnalyzer = SEARCH_ANALYZER)
    private String answer;

    /**
     * 用户id
     */
    @Field(type = FieldType.Long)
    private Long userId;
    /**
     * 编辑时间
     */
    @Field(type = FieldType.Date, format = {}, pattern = DATE_TIME_PATTERN)
    private Date editTime;
    /**
     * 创建时间
     */
    @Field(type = FieldType.Date, format = {}, pattern = DATE_TIME_PATTERN)
    private Date createTime;

    /**
     * 更新时间
     */
    @Field(type = FieldType.Date, format = {}, pattern = DATE_TIME_PATTERN)
    private Date updateTime;

    /**
     * 是否删除（keyword：代码中以字符串 "0" 做 termQuery 过滤）
     */
    @Field(type = FieldType.Keyword)
    private Integer isDelete;

    private static final long serialVersionUID = 1L;

    /**
     * 对象转包装类
     *
     * @param question
     * @return
     */
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

    /**
     * 包装类转对象
     *
     * @param questionEsDTO
     * @return
     */
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
