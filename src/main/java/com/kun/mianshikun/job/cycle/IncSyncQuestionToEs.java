package com.kun.mianshikun.job.cycle;

import cn.hutool.core.collection.CollUtil;
import com.kun.mianshikun.esdao.QuestionEsDao;
import com.kun.mianshikun.mapper.QuestionMapper;
import com.kun.mianshikun.model.dto.question.QuestionEsDTO;
import com.kun.mianshikun.model.entity.Question;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class IncSyncQuestionToEs {

    private static final int PAGE_SIZE = 500;

    private static final Integer IS_DELETE = 1;

    @Resource
    private QuestionMapper questionMapper;

    @Resource
    private QuestionEsDao questionEsDao;

    @Scheduled(fixedRate = 60 * 1000)
    public void run() {
        try {
            // 查询近 5 分钟内变更的数据（含已逻辑删除的行，删除同样要同步到 ES）
            Date fiveMinutesAgoDate = new Date(new Date().getTime() - 5 * 60 * 1000L);
            List<Question> questionList = questionMapper.findListWithDelete(fiveMinutesAgoDate);
            if (CollUtil.isEmpty(questionList)) {
                log.info("no inc question");
                return;
            }

            // 逻辑删除的题目：ES 是从 MySQL 重建的派生数据，直接从索引移除，不保留垃圾文档
            List<Long> deletedIdList = questionList.stream()
                    .filter(q -> IS_DELETE.equals(q.getIsDelete()))
                    .map(Question::getId)
                    .collect(Collectors.toList());
            List<QuestionEsDTO> questionEsDTOList = questionList.stream()
                    .filter(q -> !IS_DELETE.equals(q.getIsDelete()))
                    .map(QuestionEsDTO::objToDto)
                    .collect(Collectors.toList());

            log.info("IncSyncQuestionToEs start, upsert {}, delete {}",
                    questionEsDTOList.size(), deletedIdList.size());

            if (CollUtil.isNotEmpty(deletedIdList)) {
                try {
                    questionEsDao.deleteAllById(deletedIdList);
                } catch (Exception e) {
                    // ES 删除失败不影响正常同步：查询侧仍有 isDelete=0 过滤兜底，下一轮会重试
                    log.error("ES 删除已删题目失败, ids={}", deletedIdList, e);
                }
            }
            for (int i = 0; i < questionEsDTOList.size(); i += PAGE_SIZE) {
                int end = Math.min(i + PAGE_SIZE, questionEsDTOList.size());
                log.info("sync from {} to {}", i, end);
                questionEsDao.saveAll(questionEsDTOList.subList(i, end));
            }
            log.info("IncSyncQuestionToEs end, upsert {}, delete {}",
                    questionEsDTOList.size(), deletedIdList.size());
        } catch (Exception e) {
            log.error("IncSyncQuestionToEs error", e);
        }
    }
}
