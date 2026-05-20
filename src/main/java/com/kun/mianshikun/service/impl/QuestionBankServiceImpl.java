package com.kun.mianshikun.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kun.mianshikun.model.entity.QuestionBank;
import com.kun.mianshikun.mapper.QuestionBankMapper;
import com.kun.mianshikun.service.QuestionBankService;
import org.springframework.stereotype.Service;

@Service
public class QuestionBankServiceImpl extends ServiceImpl<QuestionBankMapper, QuestionBank> implements QuestionBankService {
}
