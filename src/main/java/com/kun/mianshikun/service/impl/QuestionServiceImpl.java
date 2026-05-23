package com.kun.mianshikun.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kun.mianshikun.common.ErrorCode;
import com.kun.mianshikun.constant.CommonConstant;
import com.kun.mianshikun.exception.BusinessException;
import com.kun.mianshikun.exception.ThrowUtils;
import com.kun.mianshikun.mapper.QuestionMapper;
import com.kun.mianshikun.model.dto.question.QuestionQueryRequest;
import com.kun.mianshikun.model.entity.Question;
import com.kun.mianshikun.model.entity.QuestionBankQuestion;
import com.kun.mianshikun.model.entity.User;
import com.kun.mianshikun.model.vo.QuestionVO;
import com.kun.mianshikun.model.vo.UserVO;
import com.kun.mianshikun.service.QuestionBankQuestionService;
import com.kun.mianshikun.service.QuestionService;
import com.kun.mianshikun.service.UserService;
import com.kun.mianshikun.utils.SqlUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {
    @Resource
    private QuestionBankQuestionService questionBankQuestionService;
    @Resource
    private UserService userService;

    @Override
    public void validQuestion(Question question, boolean add) {
        if (question == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String title = question.getTitle();
        String content = question.getContent();
        String answer = question.getAnswer();
        String tags = question.getTags();
        if (add) {
            ThrowUtils.throwIf(StringUtils.isAnyBlank(title, content, answer), ErrorCode.PARAMS_ERROR);
        }
        if (StringUtils.isNotBlank(title) && title.length() > 80) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题过长");
        }
        if (StringUtils.isNotBlank(content) && content.length() > 10240) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "内容过长");
        }
        if (StringUtils.isNotBlank(answer) && answer.length() > 10240) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "答案过长");
        }
        if (StringUtils.isNotBlank(tags)) {
            List<String> tagList = JSONUtil.toList(tags, String.class);
            if (CollUtil.isEmpty(tagList)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "标签格式错误");
            }
        }
    }

    @Override
    public QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest) {
        QueryWrapper<Question> queryWrapper = new QueryWrapper<>();
        if (questionQueryRequest == null) {
            return queryWrapper;
        }
        if (questionQueryRequest.getQuestionBankId() !=null){
            List<QuestionBankQuestion> qbq_list = questionBankQuestionService.list(new QueryWrapper<QuestionBankQuestion>()
                    .eq("questionBankId", questionQueryRequest.getQuestionBankId()));
            log.info("qbq_list:{}",qbq_list);
            List<Long> questionIdList = qbq_list.stream()
                    .map(QuestionBankQuestion::getQuestionId)
                    .collect(Collectors.toList());
            log.info("questionIdList:{}",questionIdList);
            if (questionIdList != null && questionIdList.size() > 0){
                queryWrapper.in("id", questionIdList);
            }else{
                queryWrapper.eq("id", -1);
            }
        }
        String searchText = questionQueryRequest.getSearchText();
        String sortField = questionQueryRequest.getSortField();
        String sortOrder = questionQueryRequest.getSortOrder();
        Long id = questionQueryRequest.getId();
        Long notId = questionQueryRequest.getNotId();
        String title = questionQueryRequest.getTitle();
        String content = questionQueryRequest.getContent();
        List<String> tagList = questionQueryRequest.getTags();
        String answer = questionQueryRequest.getAnswer();
        Long userId = questionQueryRequest.getUserId();
        Long questionBankId = questionQueryRequest.getQuestionBankId();

        if (StringUtils.isNotBlank(searchText)) {
            queryWrapper.and(qw -> qw.like("title", searchText).or().like("content", searchText));
        }
        queryWrapper.like(StringUtils.isNotBlank(title), "title", title);
        queryWrapper.like(StringUtils.isNotBlank(content), "content", content);
        queryWrapper.like(StringUtils.isNotBlank(answer), "answer", answer);
        if (CollUtil.isNotEmpty(tagList)) {
            for (String tag : tagList) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), CommonConstant.SORT_ORDER_ASC.equals(sortOrder),
                sortField);
        return queryWrapper;
    }

    @Override
    public QuestionVO getQuestionVO(Question question) {
        if (question == null) {
            return null;
        }
        QuestionVO questionVO = QuestionVO.objToVo(question);
        Long userId = question.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            questionVO.setUser(userVO);
        }
        return questionVO;
    }

    @Override
    public Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage) {
        List<Question> questionList = questionPage.getRecords();
        Page<QuestionVO> questionVOPage = new Page<>(questionPage.getCurrent(), questionPage.getSize(),
                questionPage.getTotal());
        if (CollUtil.isEmpty(questionList)) {
            return questionVOPage;
        }
        Set<Long> userIdSet = questionList.stream().map(Question::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        List<QuestionVO> questionVOList = questionList.stream().map(question -> {
            QuestionVO questionVO = QuestionVO.objToVo(question);
            Long userId = question.getUserId();
            if (userIdUserListMap.containsKey(userId)) {
                User user = userIdUserListMap.get(userId).get(0);
                questionVO.setUser(userService.getUserVO(user));
            }
            return questionVO;
        }).collect(Collectors.toList());
        questionVOPage.setRecords(questionVOList);
        return questionVOPage;
    }
}
