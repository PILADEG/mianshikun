package com.kun.mianshikun.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@TableName(value = "question_bank_question")
@Data
public class QuestionBankQuestion implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long questionBankId;

    private Long questionId;

    private Long userId;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
