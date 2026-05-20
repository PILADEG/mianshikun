package com.kun.mianshikun.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kun.mianshikun.model.entity.Question;
import com.kun.mianshikun.mapper.QuestionMapper;
import com.kun.mianshikun.service.QuestionService;
import org.springframework.stereotype.Service;

@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {
}
