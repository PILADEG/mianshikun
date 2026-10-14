package com.kun.mianshikun.job.cycle;

import cn.hutool.core.collection.CollUtil;
import com.kun.mianshikun.esdao.QuestionEsDao;
import com.kun.mianshikun.mapper.QuestionMapper;
import com.kun.mianshikun.model.dto.question.QuestionEsDTO;
import com.kun.mianshikun.model.entity.Question;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    // 同步水位：上次成功同步到的 (updateTime, id)；删掉这个 key 即触发一次全量重建
    private static final String CURSOR_KEY = "question:es:sync:cursor";

    // 稳定期：只同步 5 分钟前修改的数据，让同一行的连续修改合并、并给事务提交留时间
    private static final long SETTLE_MILLIS = 5 * 60 * 1000L;

    @Resource
    private QuestionMapper questionMapper;

    @Resource
    private QuestionEsDao questionEsDao;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Scheduled(fixedRate = 60 * 1000)
    public void run() {
        try {
            Date endTime = new Date(System.currentTimeMillis() - SETTLE_MILLIS);

            // 水位：没有水位就从最早开始，等价于一次分页全量（分页拉，不会把整表读进内存）
            Date lastTime = new Date(0L);
            Long lastId = 0L;
            String cursor = stringRedisTemplate.opsForValue().get(CURSOR_KEY);
            if (StringUtils.isNotBlank(cursor)) {
                String[] parts = cursor.split("\\|");
                lastTime = new Date(Long.parseLong(parts[0]));
                lastId = Long.parseLong(parts[1]);
            }

            int total = 0;
            while (true) {
                List<Question> page = questionMapper.findListByCursor(lastTime, lastId, endTime, PAGE_SIZE);
                if (CollUtil.isEmpty(page)) {
                    break;
                }

                // 逻辑删除的题目：ES 是从 MySQL 重建的派生数据，直接从索引移除，不保留垃圾文档。
                // 这里失败就让整轮失败、水位不推进——漏删会在 ES 里留下永远清不掉的孤儿文档
                List<Long> deletedIdList = page.stream()
                        .filter(q -> IS_DELETE.equals(q.getIsDelete()))
                        .map(Question::getId)
                        .collect(Collectors.toList());
                if (CollUtil.isNotEmpty(deletedIdList)) {
                    questionEsDao.deleteAllById(deletedIdList);
                }

                // 写入：单条失败只记日志，不让一条坏数据拖住它后面的所有数据
                for (Question question : page) {
                    if (IS_DELETE.equals(question.getIsDelete())) {
                        continue;
                    }
                    try {
                        questionEsDao.save(QuestionEsDTO.objToDto(question));
                    } catch (Exception e) {
                        log.error("ES 写入失败, questionId={}", question.getId(), e);
                    }
                }

                // 整页处理完才推进水位
                Question last = page.get(page.size() - 1);
                lastTime = last.getUpdateTime();
                lastId = last.getId();
                stringRedisTemplate.opsForValue().set(CURSOR_KEY, lastTime.getTime() + "|" + lastId);
                total += page.size();
            }

            if (total > 0) {
                log.info("IncSyncQuestionToEs end, total {}", total);
            }else log.info("IncSyncQuestionToEs end, no data");
        } catch (Exception e) {
            log.error("IncSyncQuestionToEs error, 水位保持不动，下一轮重试", e);
        }
    }
}
