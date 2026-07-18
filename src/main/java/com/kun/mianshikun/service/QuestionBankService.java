package com.kun.mianshikun.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kun.mianshikun.model.dto.questionBank.QuestionBankQueryRequest;
import com.kun.mianshikun.model.entity.QuestionBank;
import com.kun.mianshikun.model.vo.QuestionBankVO;

public interface QuestionBankService extends IService<QuestionBank> {

    void validQuestionBank(QuestionBank questionBank, boolean add);

    QueryWrapper<QuestionBank> getQueryWrapper(QuestionBankQueryRequest questionBankQueryRequest);

    QuestionBankVO getQuestionBankVO(QuestionBank questionBank);

    QuestionBankVO getQuestionBankVOById(Long id, Boolean needQueryQuestionList, Integer current, Integer pageSize);

    Page<QuestionBankVO> getQuestionBankVOPage(Page<QuestionBank> questionBankPage);
}
