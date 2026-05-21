package com.kun.mianshikun.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kun.mianshikun.model.dto.question.QuestionQueryRequest;
import com.kun.mianshikun.model.entity.Question;
import com.kun.mianshikun.model.vo.QuestionVO;

public interface QuestionService extends IService<Question> {

    void validQuestion(Question question, boolean add);

    QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest);

    QuestionVO getQuestionVO(Question question);

    Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage);
}
