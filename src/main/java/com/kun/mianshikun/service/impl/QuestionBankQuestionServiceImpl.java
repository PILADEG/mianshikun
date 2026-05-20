package com.kun.mianshikun.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kun.mianshikun.model.entity.QuestionBankQuestion;
import com.kun.mianshikun.mapper.QuestionBankQuestionMapper;
import com.kun.mianshikun.service.QuestionBankQuestionService;
import org.springframework.stereotype.Service;

@Service
public class QuestionBankQuestionServiceImpl extends ServiceImpl<QuestionBankQuestionMapper, QuestionBankQuestion> implements QuestionBankQuestionService {
}
