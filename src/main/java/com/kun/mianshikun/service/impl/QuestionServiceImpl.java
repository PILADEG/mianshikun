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
import com.kun.mianshikun.model.dto.post.PostEsDTO;
import com.kun.mianshikun.model.dto.question.QuestionEsDTO;
import com.kun.mianshikun.model.dto.question.QuestionQueryRequest;
import com.kun.mianshikun.model.entity.*;
import com.kun.mianshikun.model.vo.QuestionVO;
import com.kun.mianshikun.model.vo.UserVO;
import com.kun.mianshikun.mapper.QuestionBankMapper;
import com.kun.mianshikun.service.QuestionBankQuestionService;
import com.kun.mianshikun.service.QuestionService;
import com.kun.mianshikun.service.UserService;
import com.kun.mianshikun.utils.SqlUtils;

import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.redis.core.BoundGeoOperations;
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
    @Resource
    private QuestionBankMapper questionBankMapper;
    @Resource
    private ElasticsearchRestTemplate elasticsearchRestTemplate;
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
            Long tagSet = tagList.stream().distinct().count();
            if (tagSet != tagList.size()){
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "标签重复");
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
    public List<Question> getQuestionBanks(List<Question> questions) {
        for (Question question : questions){
            List<QuestionBankQuestion> qbq_list =
                    questionBankQuestionService
                    .list(
                    new QueryWrapper<QuestionBankQuestion>()
                    .eq("questionId", question.getId()));
            if (qbq_list != null && qbq_list.size() > 0){
                List<Long> questionBankIdList = qbq_list.stream()
                        .map(QuestionBankQuestion::getQuestionBankId)
                        .collect(Collectors.toList());
                List<QuestionBank> questionBankList = questionBankMapper.selectList(
                        new QueryWrapper<QuestionBank>().in("id", questionBankIdList));
                question.setQuestionBanks(questionBankList);
            }
        }
        return questions;
    }

    @Override
    public Page<Question> searchFromES(QuestionQueryRequest questionQueryRequest) {
        try {
            return searchFromEsInternal(questionQueryRequest);
        } catch (Exception e) {
            log.warn("ES 查询失败，降级为数据库查询", e);
            QueryWrapper<Question> queryWrapper = getQueryWrapper(questionQueryRequest);
            return this.page(new Page<>(questionQueryRequest.getCurrent(), questionQueryRequest.getPageSize()), queryWrapper);
        }
    }

    private Page<Question> searchFromEsInternal(QuestionQueryRequest questionQueryRequest) {
        Long id = questionQueryRequest.getId();
        Long notId = questionQueryRequest.getNotId();
        String searchText = questionQueryRequest.getSearchText();
        String title = questionQueryRequest.getTitle();
        String content = questionQueryRequest.getContent();
        List<String> tagList = questionQueryRequest.getTags();
        Long userId = questionQueryRequest.getUserId();
        String answer = questionQueryRequest.getAnswer();
        // es 起始页为 0
        long current = questionQueryRequest.getCurrent() - 1;
        long pageSize = questionQueryRequest.getPageSize();
        String sortField = questionQueryRequest.getSortField();
        String sortOrder = questionQueryRequest.getSortOrder();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        // 过滤
        boolQueryBuilder.filter(QueryBuilders.termQuery("isDelete", 0));
        if (id != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("id", id));
        }
        if (notId != null) {
            boolQueryBuilder.mustNot(QueryBuilders.termQuery("id", notId));
        }
        if (userId != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("userId", userId));
        }
        // 必须包含所有标签
        if (CollUtil.isNotEmpty(tagList)) {
            for (String tag : tagList) {
                boolQueryBuilder.filter(QueryBuilders.termQuery("tags", tag));
            }
        }
        // 按关键词检索
        if (StringUtils.isNotBlank(searchText)) {
            boolQueryBuilder.should(QueryBuilders.matchQuery("title", searchText));
            boolQueryBuilder.should(QueryBuilders.matchQuery("content", searchText));
            boolQueryBuilder.should(QueryBuilders.matchQuery("answer", searchText));
            boolQueryBuilder.minimumShouldMatch(1);
        }
        // 按标题检索
        if (StringUtils.isNotBlank(title)) {
            boolQueryBuilder.should(QueryBuilders.matchQuery("title", title));
            boolQueryBuilder.minimumShouldMatch(1);
        }
        // 按内容检索
        if (StringUtils.isNotBlank(content)) {
            boolQueryBuilder.should(QueryBuilders.matchQuery("content", content));
            boolQueryBuilder.minimumShouldMatch(1);
        }
        if (StringUtils.isNotBlank(answer)){
            boolQueryBuilder.should(QueryBuilders.matchQuery("answer", answer));
            boolQueryBuilder.minimumShouldMatch(1);
        }
        // 排序
        SortBuilder<?> sortBuilder = SortBuilders.scoreSort();
        if (StringUtils.isNotBlank(sortField)) {
            sortBuilder = SortBuilders.fieldSort(sortField);
            sortBuilder.order(CommonConstant.SORT_ORDER_ASC.equals(sortOrder) ? SortOrder.ASC : SortOrder.DESC);
        }
        // 分页
        PageRequest pageRequest = PageRequest.of((int) current, (int) pageSize);
        // 构造查询
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(boolQueryBuilder)
                .withPageable(pageRequest).withSorts(sortBuilder).build();
        SearchHits<QuestionEsDTO> searchHits =
                elasticsearchRestTemplate.search(searchQuery, QuestionEsDTO.class);
        Page<Question> page = new Page<>();
        page.setTotal(searchHits.getTotalHits());
        List<Question> resourceList = new ArrayList<>();
        if (searchHits.hasSearchHits()){
            for (SearchHit<QuestionEsDTO> searchHit : searchHits.getSearchHits()){
                Question question = QuestionEsDTO.dtoToObj(searchHit.getContent());
                resourceList.add( question);
            }
        }
        page.setRecords(resourceList);
        return page;
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
