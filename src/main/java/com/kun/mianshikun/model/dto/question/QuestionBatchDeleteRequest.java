package com.kun.mianshikun.model.dto.question;

import lombok.Data;
import java.util.List;
import java.io.Serializable;
@Data
public class QuestionBatchDeleteRequest implements Serializable {
    public List<Long> questionIdList;
    private static final long serialVersionUID = 1L;
}
