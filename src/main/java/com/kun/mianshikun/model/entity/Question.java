package com.kun.mianshikun.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.kun.mianshikun.model.vo.QuestionBankQuestionVO;
import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.io.Serializable;
import java.util.Date;

@TableName(value = "question")
@Data
public class Question implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String title;

    private String content;

    private String tags;

    private String answer;

    private Long userId;

    private Date editTime;

    private Date createTime;

    @TableField(exist = false)
    private List<QuestionBank> questionBanks;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
