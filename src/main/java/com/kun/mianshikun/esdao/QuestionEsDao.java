package com.kun.mianshikun.esdao;

import com.kun.mianshikun.model.dto.question.QuestionEsDTO;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

@Lazy
public interface QuestionEsDao extends ElasticsearchRepository<QuestionEsDTO, Long> {
    List<QuestionEsDTO> findByUserId(Long userId);
}
